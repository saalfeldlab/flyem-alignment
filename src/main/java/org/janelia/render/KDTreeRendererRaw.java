package org.janelia.render;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.concurrent.Callable;

import javax.imageio.ImageIO;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.KeyStrokeAdder.Factory;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import org.janelia.render.SynPrediction.SynCollection;
import org.janelia.render.TbarPrediction.TbarCollection;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.imglib2.RandomAccessibleLoader;

import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.cache.CacheControl;
import bdv.tools.transformation.TransformedSource;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.BasicViewerState;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import bdv.viewer.render.MultiResolutionRenderer;
import bdv.viewer.render.PainterThread;
import bdv.viewer.render.RenderTarget;
import bdv.viewer.render.awt.BufferedImageRenderResult;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.display.screenimage.awt.ARGBScreenImage;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.0.1-SNAPSHOT" )
public class KDTreeRendererRaw<T extends RealType<T>,P extends RealLocalizable> implements Callable<Void>
{

	@Option(names = { "-s", "--synapsePath"}, required = false, description = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
	private List<String> synapsePaths = new ArrayList<>();

	@Option(names = { "-r", "--radius"}, required = true, description = "Radius for synapse point spread function")
	private double radius;

	@Option(names = { "-p", "--n5Path"}, required = false, description = "N5 path")
	private String n5Path = "";

	@Option(names = { "-i", "--n5Datasets"}, required = false, description = "N5 image datasets")
	private List<String> images = new ArrayList<>();

	@Option( names = { "--stephan" }, required = false, description = "" )
	private boolean stephan = false;

	@Option( names = { "-o",  "--output"}, required = false, description = "The output file." )
	private String outputFile;
		

	static final double searchDist = 150;
	static final double searchDistSqr = searchDist * searchDist;
	static final double invSquareSearchDistance = 1.0 / searchDist / searchDist; 
	
	KDTree< T > tree;
	Interval itvl;

	public KDTreeRendererRaw()
	{
	}

	public KDTreeRendererRaw( List<T> vals, List<P> pts )
	{
		buildTree( vals, pts );
	}
	
	public void buildTree( List<T> vals, List<P> pts )
	{
		tree = new KDTree< T >( vals, pts );
	}

	public void setInterval( Interval itvl )
	{
		this.itvl = itvl;
	}
	
	public Interval getInterval()
	{
		return itvl;
	}
	
	public RBFInterpolator<T> getInterp( 
			final double searchDist,
			final DoubleUnaryOperator rbf )
	{
		return new RBFInterpolator.RBFInterpolatorFactory< T >( 
						rbf, searchDist, false,
						tree.firstElement().copy() ).create( tree );
	}
	
	public KDTree<T> getTree()
	{
		return tree;
	}

	public RealRandomAccessible<T> getRealRandomAccessible(
			final double searchDist,
			final DoubleUnaryOperator rbf )
	{
		RBFInterpolator.RBFInterpolatorFactory< T > interp = 
				new RBFInterpolator.RBFInterpolatorFactory< T >( 
						rbf, searchDist, false,
						tree.firstElement().copy() );

		return Views.interpolate( tree, interp );
	}

	public static double rbf( final double rsqr )
	{
		if( rsqr > searchDistSqr )
			return 0;
		else
			return 50 *( 1 - ( rsqr * invSquareSearchDistance ));
	}
	
	public Void call() throws IOException
	{
		
		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );
		
		double[] xfmArray = new double[]{
				0.03125, 0.0, 0.0, 0.0,
				0.0, 0.03125, 0.0, 0.0,
				0.0, 0.0, 0.03125, 0.0 };
		
		AffineTransform3D xfm = new AffineTransform3D();
		xfm.set( xfmArray );
		
		
		BdvOptions bdvOpts = BdvOptions.options().numRenderingThreads( 24 )
				.preferredSize( 1280, 1024 );

		
		AffineTransform3D transform = toFlyEm;
		if( stephan )
		{
			System.out.println("Stephan space");
			// load method can save a step if it doesn't have to apply a transform
			// so prefer that over applying the identity
			transform = null;  
		}
		
		
		BdvStackSource<?> bdv = null;
		bdv = loadImages( 
				n5Path,
				images,
				transform,
				new FinalVoxelDimensions("nm", new double[]{8, 8, 8}),
				true, bdv, bdvOpts );

		bdv.getBdvHandle().getViewerPanel().getState().setViewerTransform( xfm );
		
		double amount = 20;
		//RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>( amount );
		RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>();

		ARGBType magenta = new ARGBType( ARGBType.rgba(255, 0, 255, 255));

		for (int i = 0; i < synapsePaths.size(); ++i)
		{

			File synapseFile = new File( synapsePaths.get( i )); 

			// load synapses
			KDTreeRendererRaw<DoubleType,RealPoint> treeRenderer = load( synapseFile.getAbsolutePath() );
			Interval itvl = treeRenderer.getInterval();

			
			// This works
			RealRandomAccessible<DoubleType> source = treeRenderer.getRealRandomAccessible( radius, KDTreeRendererRaw::rbf );

//			This doesn't work
			//RBFInterpolator<DoubleType> interp = treeRenderer.getInterp( options.getRadius(), KDTreeRendererRaw::rbf );
			//rc.add( interp );
//			RealRandomAccessible< DoubleType > source = new FixedInterpolant<DoubleType>( interp );

			
			//RBFRealRandomAccessible<DoubleType> source = new RBFRealRandomAccessible<>( treeRenderer, options.getRadius(), amount );
//			rc.add( source );

			bdvOpts = bdvOpts.addTo( bdv );

			bdv = BdvFunctions.show( source, itvl, "tbar render", bdvOpts );
//			bdv.getSources().get(0).getSpimSource().getInterpolatedSource( 0, 0, null ).realRandomAccess();
			bdv.setDisplayRange( 0, 800 );
			//bdv.setColor(magenta);
			
			
//			RealRandomAccessible<DoubleType> sourceSmall = treeRenderer.getRealRandomAccessible( 25, KDTreeRendererRaw::rbf );
//			bdvOpts = bdvOpts.addTo( bdv );
//
//			bdv = BdvFunctions.show( sourceSmall, itvl, "tbar small render", bdvOpts );
//			bdv.setDisplayRange( 0, 150 );
//			bdv.setColor(magenta);
		}

//		RadiusKeyListener rkl = new RadiusKeyListener();

		
		InputTriggerConfig trigConfig = bdv.getBdvHandle().getViewerPanel().getOptionValues().getInputTriggerConfig();
		if( trigConfig == null )
			trigConfig = new InputTriggerConfig();
		
		RKActions.installActionBindings( bdv.getBdvHandle().getKeybindings(), bdv.getBdvHandle().getViewerPanel(), 
				rc, trigConfig );

//		recordMovie( bdv.getBdvHandle().getViewerPanel() );

		return null;
	}
	
	public static void main( String[] args ) throws ImgLibException, IOException
	{
		CommandLine.call( new KDTreeRendererRaw(), args );
	}
	
	public static void recordMovie( ViewerPanel viewer )
	{
		final int width = 1280;
		final int height = 1024;
		String dir = "/groups/saalfeld/home/bogovicj/record";
		
		final ViewerState renderState = new BasicViewerState( viewer.state().snapshot() );
		
		int start = -715;
		int end = -350;
		int step = 10;
		double[] xfmArray = new double[]{
				-0.02420699152030538, 2.6020852139652106E-18, -4.336808689942018E-18, 1105.676175769312, 
				-7.806255641895632E-18, -8.673617379884035E-18, 0.024206991520305372, 29.14286687653413,
				-8.673617379884035E-19, 0.02420699152030538, 5.204170427930421E-18, start };
		
		AffineTransform3D xfm = new AffineTransform3D();
		xfm.set( xfmArray );

		class MyTarget implements RenderTarget< BufferedImageRenderResult >
		{
			final ARGBScreenImage accumulated = new ARGBScreenImage( width, height );

			final BufferedImageRenderResult renderResult = new BufferedImageRenderResult();

			public void clear()
			{
				for ( final ARGBType acc : accumulated )
					acc.setZero();
			}

			@Override
			public BufferedImageRenderResult getReusableRenderResult()
			{
				return renderResult;
			}

			@Override
			public BufferedImageRenderResult createRenderResult()
			{
				return new BufferedImageRenderResult();
			}

			@Override
			public void setRenderResult( final BufferedImageRenderResult renderResult )
			{
				final BufferedImage bufferedImage = renderResult.getBufferedImage();
				final Img< ARGBType > argbs = ArrayImgs.argbs( ( ( DataBufferInt ) bufferedImage.getData().getDataBuffer() ).getData(), width, height );
				final Cursor< ARGBType > c = argbs.cursor();
				for ( final ARGBType acc : accumulated )
				{
					final int current = acc.get();
					final int in = c.next().get();
					acc.set( ARGBType.rgba(
							Math.max( ARGBType.red( in ), ARGBType.red( current ) ),
							Math.max( ARGBType.green( in ), ARGBType.green( current ) ),
							Math.max( ARGBType.blue( in ), ARGBType.blue( current ) ),
							Math.max( ARGBType.alpha( in ), ARGBType.alpha( current ) )	) );
				}
			}

			@Override
			public final int getWidth()
			{
				return width;
			}

			@Override
			public int getHeight()
			{
				return height;
			}
		}

		final MyTarget target = new MyTarget();
		
		MultiResolutionRenderer renderer = new MultiResolutionRenderer(
				target, 
				new PainterThread( null ), 
				new double[]{1}, 
				0, 
				1, 
				null, 
				false, 
				viewer.getOptionValues().getAccumulateProjectorFactory(), 
				new CacheControl.Dummy());
		
		renderState.setViewerTransform( xfm );

		for ( int z = start; z < end; z+= step )
		{
			renderer.requestRepaint();
			renderer.paint( renderState );
			
			xfm.set( z, 2, 3);
			renderState.setViewerTransform( xfm );

			try 
			{

				final BufferedImage bi = target.accumulated.image();
				ImageIO.write( bi, "png", new File( String.format( "%s/img-%04d.png", dir, z ) ) );
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			System.out.println( "slice: " + z );
		}
	}

	public static class RKActions extends Actions
	{
		public final static String SAYHI = "sayhi";
		public final static String INCREASE = "increase";
		public final static String DECREASE = "decrease";
		public final static String PRINTXFM = "printxfm";

		
		public static void installActionBindings(
				final InputActionBindings inputActionBindings,
				final ViewerPanel viewerPanel,
				final RadiusChange<DoubleType> rc,
				final KeyStrokeAdder.Factory keyProperties)
		{
			final RKActions rka = new RKActions( keyProperties );
			rka.hi( viewerPanel );
			rka.increase( rc );
			rka.decrease( rc );
			rka.printTransform( viewerPanel );
			rka.install( inputActionBindings, "rka" );
		}

		public RKActions( Factory keyConfig ) {
			super( keyConfig, new String[]{ "rad" });
		}
		
		public void hi( final ViewerPanel viewer )
		{
			runnableAction( 
					() -> viewer.showMessage("hi"),
					SAYHI, "H");
		}

		public void increase( RadiusChange<DoubleType> rc )
		{
			runnableAction( 
					() -> rc.increase(),
					INCREASE, "B");
		}

		public void decrease( RadiusChange<DoubleType> rc )
		{
			runnableAction( 
					() -> rc.decrease(),
					DECREASE, "V");
		}

		public void printTransform( final ViewerPanel vp )
		{
			runnableAction( 
					() -> printXfm( vp ),
					PRINTXFM, "J");
		}
		
		public static void printXfm( final ViewerPanel vp )
		{
			AffineTransform3D xfm = new AffineTransform3D();
			vp.getState().getViewerTransform( xfm );
			System.out.println( xfm );
		}
		
	}
	

	public static class RBFRealRandomAccessible<T extends RealType<T>> implements RealRandomAccessible<T>
	{
		final RBFInterpolator.RBFInterpolatorFactory<T> interpFactory;
		final RBFInterpolator<T> interp;
		final KDTreeRendererRaw<T,?> kdtr;
		final double amount;
		public RBFRealRandomAccessible( KDTreeRendererRaw<T,?> kdtr, 
				double startingRad,
				double amount )
		{
			this.amount = amount;
			this.kdtr = kdtr;
			this.interpFactory = 
					new RBFInterpolator.RBFInterpolatorFactory< T >( 
							KDTreeRendererRaw::rbf, startingRad, false,
							kdtr.tree.firstElement().copy() );
			interp = interpFactory.create( kdtr.tree );
		}
		
		public void increase()
		{
			interp.increaseRadius(amount);
		}
		
		public void decrease()
		{
			interp.decreaseRadius(amount);
		}

		@Override
		public int numDimensions() {
			return kdtr.tree.numDimensions();
		}

		@Override
		public RealRandomAccess<T> realRandomAccess() {
			return interp;
		}

		@Override
		public RealRandomAccess<T> realRandomAccess(RealInterval arg0) {
			return interp;
		}
		
	}
	
	public static class FixedInterpolant< T > implements RealRandomAccessible< T >
	{
		final RealRandomAccess<T> rra;

		public FixedInterpolant( final RealRandomAccess<T> rra )
		{
			this.rra = rra;
		}

		@Override
		public int numDimensions()
		{
			return rra.numDimensions();
		}

		@Override
		public RealRandomAccess< T > realRandomAccess()
		{
			return rra;
		}

		@Override
		public RealRandomAccess< T > realRandomAccess( final RealInterval interval )
		{
			return rra;
		}
	}
	
	private static class RadiusChangeInterp<T extends RealType<T>>
	{
//		private static final long serialVersionUID = 4552199070684569689L;

		private final ArrayList<RBFInterpolator<T>> interpList;
		private final double amount;
		public RadiusChangeInterp( double amount )
		{
			this.amount = amount;
			interpList = new ArrayList<RBFInterpolator<T>>();
		}

		public void add( RBFInterpolator<T> interp )
		{
			interpList.add( interp );
		}
		
		public void increase()
		{
			System.out.println( "increase" );
			for( RBFInterpolator<T> interp : interpList )
				interp.increaseRadius( amount );
		}

		public void decrease()
		{
			System.out.println( "decrease" );
			for( RBFInterpolator<T> interp : interpList )
				interp.decreaseRadius( amount );
		}

//		@Override
//		public void actionPerformed( ActionEvent arg0 )
//		{
//			System.out.println("action");
//			for( RBFInterpolator<T> interp : interpList )
//				interp.setRadius( 100 );
//		}
	}
	
	private static class RadiusChange<T extends RealType<T>>
	{
//		private static final long serialVersionUID = 4552199070684569689L;

		private final ArrayList<RBFRealRandomAccessible<T>> interpList;
		public RadiusChange()
		{
			interpList = new ArrayList<RBFRealRandomAccessible<T>>();
		}

		public void add( RBFRealRandomAccessible<T> interp )
		{
			interpList.add( interp );
		}
		
		public void increase()
		{
			System.out.println( "increase" );
			for( RBFRealRandomAccessible<T> interp : interpList )
				interp.increase();
		}

		public void decrease()
		{
			System.out.println( "decrease" );
			for( RBFRealRandomAccessible<T> interp : interpList )
				interp.decrease();
		}

//		@Override
//		public void actionPerformed( ActionEvent arg0 )
//		{
//			System.out.println("action");
//			for( RBFInterpolator<T> interp : interpList )
//				interp.setRadius( 100 );
//		}
	}
	
	public static BdvStackSource<?> loadImages( 
			String n5Path,
			List<String> images,
			AffineTransform3D transform,
			VoxelDimensions voxelDimensions,
			boolean useVolatile,
			BdvStackSource<?> bdv,
			BdvOptions opts ) throws IOException
	{
		if( n5Path == null || n5Path.isEmpty() || images.size() < 1 )
			return bdv;

		
		final N5Reader n5 = new N5FSReader(n5Path);
		final SharedQueue queue = new SharedQueue( 12 );

		for (int i = 0; i < images.size(); ++i)
		{
			final String datasetName = images.get(i);
			
			final int numScales = n5.list(datasetName).length;

			@SuppressWarnings("unchecked")
			final RandomAccessibleInterval<UnsignedByteType>[] mipmaps = (RandomAccessibleInterval<UnsignedByteType>[])new RandomAccessibleInterval[numScales];
			final double[][] scales = new double[numScales][];

			for (int s = 0; s < numScales; ++s) {

				final int scale = 1 << s;
				final double inverseScale = 1.0 / scale;

				final RandomAccessibleInterval<UnsignedByteType> source = N5Utils.open(n5, datasetName + "/s" + s);
				
				System.out.println("s " + s );	
				System.out.println( Util.printInterval( source )  + "\n" );

				final RealTransformSequence transformSequence = new RealTransformSequence();
				final Scale3D scale3D = new Scale3D(inverseScale, inverseScale, inverseScale);
				transformSequence.add(scale3D);

				final RandomAccessibleInterval<UnsignedByteType> cachedSource = wrapAsVolatileCachedCellImg(source, new int[]{64, 64, 64});

				mipmaps[s] = cachedSource;
				scales[s] = new double[]{scale, scale, scale};
			}

			final RandomAccessibleIntervalMipmapSource<?> mipmapSource =
					new RandomAccessibleIntervalMipmapSource<>(
							mipmaps,
							new UnsignedByteType(),
							scales,
							voxelDimensions,
							datasetName);

			final Source<?> volatileMipmapSource;
			if (useVolatile)
				volatileMipmapSource = mipmapSource.asVolatile(queue);
			else
				volatileMipmapSource = mipmapSource;
			
			Source<?> source2render = volatileMipmapSource;
			if( transform != null  )
			{
				System.out.println("FlyEM space");
				TransformedSource<?> transformedSource = new TransformedSource<>(volatileMipmapSource);
				transformedSource.setFixedTransform( transform );
				source2render = transformedSource;
			}

			bdv = mipmapSource( source2render, bdv, opts );
		}
		return bdv;
	}

	public static KDTreeRendererRaw<DoubleType,RealPoint> load( String synapseFilePath )
	{
		System.out.println( synapseFilePath );

		boolean success = false;
		KDTreeRendererRaw<DoubleType,RealPoint> treeRenderer = null;
		Interval itvl;
		try
		{
			SynCollection<DoubleType> synapses = SynPrediction.loadAll( synapseFilePath, new DoubleType() );
			itvl = new FinalInterval( synapses.min, synapses.max );
			System.out.println( synapses );
			treeRenderer = new KDTreeRendererRaw<DoubleType,RealPoint>( synapses.getValues( 0 ), synapses.getPoints() );
			treeRenderer.setInterval( itvl );
			success = true;
		}
		catch( Exception e )
		{
			System.out.println( "Not a new synapse json" );
			e.printStackTrace();
		}
		
		if( success )
			return treeRenderer;
		
		try
		{
			TbarCollection<DoubleType> tbars = TbarPrediction.loadAll( synapseFilePath, new DoubleType() );
			itvl = new FinalInterval( tbars.min, tbars.max );
			System.out.println( tbars );
			treeRenderer = new KDTreeRendererRaw<DoubleType,RealPoint>( tbars.getValues( 0 ), tbars.getPoints() );
			treeRenderer.setInterval( itvl );
			success = true;
		}
		catch( Exception e )
		{
			System.out.println( "Not an old synapse json" );
			e.printStackTrace();
		}
		
		if( !success )
			System.err.println( "Could not read synapses/tbars - returning null" );
		
		return treeRenderer;
	}

	@SuppressWarnings( { "unchecked", "rawtypes" } )
	public static final <T extends NativeType<T>> RandomAccessibleInterval<T> wrapAsVolatileCachedCellImg(
			final RandomAccessibleInterval<T> source,
			final int[] blockSize) throws IOException {

		final long[] dimensions = Intervals.dimensionsAsLongArray(source);
		final CellGrid grid = new CellGrid(dimensions, blockSize);

		final RandomAccessibleLoader<T> loader = new RandomAccessibleLoader<T>(Views.zeroMin(source));

		final T type = Util.getTypeFromInterval(source);
		final Set<AccessFlags> accessFlags = AccessFlags.setOf(AccessFlags.VOLATILE );

		final CachedCellImg<T, ?> img;
		final Cache<Long, Cell<?>> cache =
				new SoftRefLoaderCache().withLoader(LoadedCellCacheLoader.get(grid, loader, type, accessFlags));
		

		if (GenericByteType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.BYTE, accessFlags));
		} else if (GenericShortType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.SHORT, accessFlags));
		} else if (GenericIntType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.INT, accessFlags));
		} else if (GenericLongType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.LONG, accessFlags));
		} else if (FloatType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.FLOAT, accessFlags));
		} else if (DoubleType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get(PrimitiveType.DOUBLE, accessFlags));
		} else {
			img = null;
		}

		return img;
	}
	
	/**
	 * Quickly visualize the slab-face series as transformed by a corresponding
	 * list of target to source transforms.
	 * @throws IOException
	 */
	public static BdvStackSource<?> mipmapSource(
			final Source<?> source,
			final Bdv bdv) throws IOException {

		return mipmapSource(source, bdv, null);
	}

	/**
	 * Quickly visualize the slab-face series as transformed by a corresponding
	 * list of target to source transforms.
	 * @throws IOException
	 */
	public static BdvStackSource<?> mipmapSource(
			final Source<?> source,
			final Bdv bdv,
			BdvOptions options) throws IOException {

		if (options == null)
			options = bdv == null ? Bdv.options() : Bdv.options().addTo(bdv);
		else if( bdv != null )
		{
			System.out.println("add to");
			options = options.addTo( bdv );
		}	
		final BdvStackSource<?> stackSource = BdvFunctions.show(source, options);
		stackSource.setDisplayRange(0, 255);
		return stackSource;
	}
	
	public static class RadiusKeyListener implements KeyListener 
	{

		@Override
		public void keyPressed(KeyEvent e) {
			System.out.println("sdfsdf");
			if( e.getID() == KeyEvent.VK_D )
			{
				System.out.println("= released");
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
			System.out.println("sdfsdf");
			if( e.getID() == KeyEvent.VK_D )
			{
				System.out.println("= released");
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {
			System.out.println("sdfsdf");
			if( e.getID() == KeyEvent.VK_D )
			{
				System.out.println("= released");
			}
		}
		
	}
	
}