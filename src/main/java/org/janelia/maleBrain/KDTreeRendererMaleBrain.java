package org.janelia.maleBrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Stream;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.KeyStrokeAdder.Factory;
import org.scijava.ui.behaviour.util.Actions;

import org.janelia.saalfeldlab.n5.imglib2.RandomAccessibleLoader;

import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
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
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.parallel.DefaultTaskExecutor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.GenericByteType;
import net.imglib2.type.numeric.integer.GenericIntType;
import net.imglib2.type.numeric.integer.GenericLongType;
import net.imglib2.type.numeric.integer.GenericShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command( version = "0.0.1-SNAPSHOT" )
public class KDTreeRendererMaleBrain<T extends RealType<T>,P extends RealLocalizable> implements Callable<Void>
{

	@Option(names = { "-s", "--synapsePath"}, required = false, description = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
	private String tbarPath;

	@Option(names = { "-r", "--radius"}, required = true, description = "Radius for synapse point spread function")
	private double radius;

	@Option(names = { "-p", "--n5Path"}, required = false, description = "N5 path")
	private String n5Path = "";

	@Option(names = { "-i", "--n5Datasets"}, required = false, description = "N5 image datasets")
	private List<String> images = new ArrayList<>();

	@Option( names = { "-o",  "--output"}, required = false, description = "The output file." )
	private String outputFile;
		

	static final double searchDist = 150;
	static final double searchDistSqr = searchDist * searchDist;
	static final double invSquareSearchDistance = 1.0 / searchDist / searchDist; 
	
	KDTree< T > tree;
	Interval itvl;

	public KDTreeRendererMaleBrain()
	{
	}

	public KDTreeRendererMaleBrain( List<T> vals, List<P> pts )
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
		
		BdvOptions bdvOpts = BdvOptions.options().numRenderingThreads( 24 )
				.preferredSize( 1280, 1024 );

//		double amount = 20;
//		//RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>( amount );
//		RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>();

		// load synapses
		System.out.println( "loading");
		KDTreeRendererMaleBrain<DoubleType,RealPoint> treeRenderer = load( tbarPath );
		Interval itvl = treeRenderer.getInterval();
		RealRandomAccessible<DoubleType> source = treeRenderer.getRealRandomAccessible( radius, KDTreeRendererMaleBrain::rbf );

		if( outputFile == null )
		{
			// This works
			BdvStackSource<DoubleType> bdv = BdvFunctions.show( source, itvl, "tbar render", bdvOpts );

		}
		else{

	//		Interval itvl = new FinalInterval(
	//				new long[]{3200, 4058, 4426},
	//				new long[]{92863, 55315, 57661});
			
			AffineTransform3D toNm = new AffineTransform3D();
			toNm.scale( 8 );
		
	//		Scale3D toRenderPixels = new Scale3D(128, 128, 128);
			Scale3D toRenderPixels = new Scale3D(512, 512, 512);

			AffineTransform3D totalTransform = new AffineTransform3D();
			totalTransform.preConcatenate(toNm);
			totalTransform.preConcatenate(toRenderPixels.inverse());

			RealPoint max = new RealPoint( Intervals.maxAsDoubleArray(itvl));
			System.out.println( max );
			totalTransform.apply(max, max);
			System.out.println( max );
			
			final Interval renderItvl = Intervals.smallestContainingInterval( new FinalRealInterval( new RealPoint(0.0, 0.0, 0.0), max));
			System.out.println( "renderItvl: " + Intervals.toString( renderItvl ));
			final ArrayImg<FloatType, FloatArray> out = ArrayImgs.floats( Intervals.dimensionsAsLongArray(renderItvl));


			System.out.println( "copying");
			IntervalView<DoubleType> img = Views.interval( Views.raster( RealViews.affine(source, totalTransform) ), renderItvl );
			LoopBuilder.setImages(img, out).multiThreaded( new DefaultTaskExecutor( Executors.newFixedThreadPool( 32 ))).forEachPixel( (x,y) -> { y.setReal( x.getRealDouble() );});

			System.out.println( "writing");
			ImagePlus imp = ImageJFunctions.wrap(out, "result");
			imp.getCalibration().pixelWidth = toRenderPixels.get(0, 0);
			imp.getCalibration().pixelHeight = toRenderPixels.get(1, 1);
			imp.getCalibration().pixelDepth = toRenderPixels.get(2, 2);
			imp.getCalibration().setUnit("nm");

			IJ.save(imp, outputFile);
			System.out.println( "done");
		}

		return null;
	}
	
	public static void main( String[] args ) throws ImgLibException, IOException
	{
		CommandLine.call( new KDTreeRendererMaleBrain(), args );
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
		final KDTreeRendererMaleBrain<T,?> kdtr;
		final double amount;
		public RBFRealRandomAccessible( KDTreeRendererMaleBrain<T,?> kdtr, 
				double startingRad,
				double amount )
		{
			this.amount = amount;
			this.kdtr = kdtr;
			this.interpFactory = 
					new RBFInterpolator.RBFInterpolatorFactory< T >( 
							KDTreeRendererMaleBrain::rbf, startingRad, false,
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

	}
	

	public static KDTreeRendererMaleBrain<DoubleType,RealPoint> load( String synapseFilePath )
	{
		System.out.println( synapseFilePath );

		boolean success = false;
		Interval itvl;

		ArrayList<RealPoint> pts = new ArrayList<>();
		ArrayList<DoubleType> vals = new ArrayList<>();

		System.out.println( "loading");
		long[] min = new long[3];
		long[] max = new long[3];
		Arrays.fill( min, Long.MAX_VALUE);
		Arrays.fill( max, Long.MIN_VALUE);

		try {
			Stream<String> lines = Files.lines(Paths.get(synapseFilePath));
			lines.forEach( l -> {
				String[] s = l.split(",");
				RealPoint p = new RealPoint( Double.parseDouble(s[0]), Double.parseDouble(s[1]), Double.parseDouble(s[2]));
				pts.add( p );
				vals.add( new DoubleType( 1 ));

				for( int d = 0; d < 3; d++ )
				{
					min[d] = p.getDoublePosition(d) < min[d] ? (long)p.getDoublePosition(d) : min[d];
					max[d] = p.getDoublePosition(d) > max[d] ? (long)p.getDoublePosition(d) : max[d];
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

		itvl = new FinalInterval( min, max );
		System.out.println( "itvl: " + Intervals.toString(itvl));

		final KDTreeRendererMaleBrain<DoubleType,RealPoint> treeRenderer = new KDTreeRendererMaleBrain<>(vals, pts);
		treeRenderer.setInterval(itvl);
		success = true;
		
		if( !success )
			System.err.println( "Could not read synapses/tbars - returning null" );
		
		System.out.println( "done");
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