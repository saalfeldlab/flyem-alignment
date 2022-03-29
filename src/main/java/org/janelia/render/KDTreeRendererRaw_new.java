package org.janelia.render;


import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import javax.imageio.ImageIO;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.KeyStrokeAdder.Factory;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.BigDataViewer;
import bdv.cache.CacheControl;
import bdv.tools.transformation.TransformedSource;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.Prefs;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.render.MultiResolutionRenderer;
import bdv.viewer.render.PainterThread;
import bdv.viewer.render.RenderTarget;
import bdv.viewer.render.awt.BufferedImageRenderResult;
import bdv.viewer.state.ViewerState;
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
import net.imglib2.display.screenimage.awt.ARGBScreenImage;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.Views;


public class KDTreeRendererRaw_new<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-s", aliases = {"--synapsePath"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private List<String> synapsePaths = new ArrayList<>();

		@Option(name = "-r", aliases = {"--radius"}, required = true, usage = "Radius for synapse point spread function")
		private double radius;

		@Option(name = "-p", aliases = {"--n5Path"}, required = false, usage = "N5 path")
		private String n5Path = "";

		
		@Option(name = "-i", aliases = {"--n5Dataset"}, required = false, usage = "N5 image datasets")
		private String dataset = "22-34";
		
		@Option( name = "--stephan", required = false, usage = "" )
		private boolean stephan = false;
		
		private boolean parsedSuccessfully;

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
				parsedSuccessfully = true;
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		/**
		 * @return the n5 image paths
		 */
		public String getDataset()
		{
			return dataset;
		}
		
		/**
		 * @return the n5 path
		 */
		public String getN5Path()
		{
			return n5Path;
		}
		
		/**
		 * @return the datasets
		 */
		public List<String> getDatasets() {

			return synapsePaths;
		}
		
		/**
		 * @return the synapse file paths
		 */
		public List<String> getSynapsePaths() {

			return synapsePaths;
		}

		/**
		 * @return the group
		 */
		public double getRadius() {

			return radius;
		}
		
		public boolean isStephanSpace()
		{
			return stephan;
		}
	}

	static final double searchDist = 150;
	static final double searchDistSqr = searchDist * searchDist;
	static final double invSquareSearchDistance = 1.0 / searchDist / searchDist; 
	
	final KDTree< T > tree;
	Interval itvl;

	public KDTreeRendererRaw_new( List<T> vals, List<P> pts )
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
	
	public static void main( String[] args ) throws ImgLibException, IOException
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

		final Options options = new Options(args);

		
		AffineTransform3D transform = toFlyEm;
		if( options.isStephanSpace() )
		{
			System.out.println("Stephan space");
			// load method can save a step if it doesn't have to apply a transform
			// so prefer that over applying the identity
			transform = null;  
		}

		//transform = new AffineTransform3D();
		
		
		BdvStackSource<?> bdv = null;
		bdv = loadImages( 
				options.getN5Path(),
				options.getDataset(),
				transform,
				new FinalVoxelDimensions("nm", new double[]{8, 8, 8}),
				true, bdv, 
				bdvOpts );

		bdv.getBdvHandle().getViewerPanel().getState().setViewerTransform( xfm );
		
		
		
		double amount = 20;
		//RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>( amount );
		RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>();

		ARGBType magenta = new ARGBType( ARGBType.rgba(255, 0, 255, 255));

		List< String > datasetNames = options.getDatasets();
		for (int i = 0; i < datasetNames.size(); ++i)
		{
			File synapseFile = new File( options.getSynapsePaths().get( i ));

			// load synapses
			KDTreeRendererRaw<DoubleType,RealPoint> treeRenderer = KDTreeRendererRaw.load( synapseFile.getAbsolutePath() );
			Interval itvl = treeRenderer.getInterval();

			
			// This works
			RealRandomAccessible<DoubleType> source = treeRenderer.getRealRandomAccessible(options.getRadius(), KDTreeRendererRaw::rbf );

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
		final KDTreeRendererRaw_new<T,?> kdtr;
		final double amount;
		public RBFRealRandomAccessible( KDTreeRendererRaw_new<T,?> kdtr, 
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

	public static <T extends NativeType<T> & RealType<T>> BdvStackSource<?> loadImages( 
			String n5Path,
			String images,
			AffineTransform3D transform,
			VoxelDimensions voxelDimensions,
			boolean useVolatile,
			BdvStackSource<?> bdv,
			BdvOptions opts ) throws IOException
	{
		N5FSReader n5 = new N5FSReader( n5Path );
		Pair<RandomAccessibleInterval<T>[], double[][]> mipmaps = N5Utils.openMipmaps(n5, images, useVolatile);
		T t = Util.getTypeFromInterval( mipmaps.getA()[0] );

		
		final RandomAccessibleIntervalMipmapSource<T> mipmapSource =
				new RandomAccessibleIntervalMipmapSource<T>(
						mipmaps.getA(),
						t,
						mipmaps.getB(),
						voxelDimensions,
						"hemibrain");

		Source<T> source2render = mipmapSource;
		if( transform != null  )
		{
//			System.out.println("FlyEM space");
			TransformedSource<T> transformedSource = new TransformedSource<>(mipmapSource);
			transformedSource.setFixedTransform( transform );
			source2render = transformedSource;
		}

		bdv = mipmapSource( source2render, bdv, opts );
		return bdv;
	}
	
	public static <T extends RealType<T> & NativeType<T>> BdvStackSource<T> loadImagesOld( 
			String n5Path,
			List<String> images,
			AffineTransform3D transform,
			VoxelDimensions voxelDimensions,
			boolean useVolatile,
			BdvStackSource<T> bdv,
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
			final RandomAccessibleInterval<T>[] mipmaps = (RandomAccessibleInterval<T>[])new RandomAccessibleInterval[numScales];
			final double[][] scales = new double[numScales][];

			for (int s = 0; s < numScales; ++s) {

				final int scale = 1 << s;
				final double inverseScale = 1.0 / scale;

				final RandomAccessibleInterval<T> source = N5Utils.open(n5, datasetName + "/s" + s);
				
				System.out.println("s " + s );	
				System.out.println( Util.printInterval( source )  + "\n" );

				final RealTransformSequence transformSequence = new RealTransformSequence();
				final Scale3D scale3D = new Scale3D(inverseScale, inverseScale, inverseScale);
				transformSequence.add(scale3D);

				final RandomAccessibleInterval<T> cachedSource = KDTreeRendererRaw.wrapAsVolatileCachedCellImg(source, new int[]{64, 64, 64});

				mipmaps[s] = cachedSource;
				scales[s] = new double[]{scale, scale, scale};
			}

			final RandomAccessibleIntervalMipmapSource<T> mipmapSource =
					new RandomAccessibleIntervalMipmapSource<>(
							mipmaps,
							Util.getTypeFromInterval( mipmaps[ 0 ]),
							scales,
							voxelDimensions,
							datasetName);

			final Source<T> volatileMipmapSource;
			if (useVolatile)
				volatileMipmapSource = (Source<T>)mipmapSource.asVolatile(queue);
			else
				volatileMipmapSource = (Source<T>)mipmapSource;
			
			Source<T> source2render = volatileMipmapSource;
			if( transform != null  )
			{
				System.out.println("FlyEM space");
				TransformedSource<T> transformedSource = new TransformedSource<>( volatileMipmapSource );
				transformedSource.setFixedTransform( transform );
				source2render = transformedSource;
			}

			bdv = mipmapSource( source2render, bdv, opts );
		}
		return bdv;
	}
	
	/**
	 * Quickly visualize the slab-face series as transformed by a corresponding
	 * list of target to source transforms.
	 * @throws IOException
	 */
	public static <T extends RealType<T> & NativeType<T>> BdvStackSource<T> mipmapSource(
			final Source<T> source,
			final Bdv bdv) throws IOException {

		return mipmapSource(source, bdv, null);
	}

	/**
	 * Quickly visualize the slab-face series as transformed by a corresponding
	 * list of target to source transforms.
	 * @throws IOException
	 */
	public static <T extends RealType<T> & NativeType<T>> BdvStackSource<T> mipmapSource(
			final Source<T> source,
			final Bdv bdv,
			BdvOptions options) throws IOException {

		if (options == null)
			options = bdv == null ? Bdv.options() : Bdv.options().addTo(bdv);
		else if( bdv != null )
		{
			System.out.println("add to");
			options = options.addTo( bdv );
		}	
		final BdvStackSource<T> stackSource = BdvFunctions.show(source, options);
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