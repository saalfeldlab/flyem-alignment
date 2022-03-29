package org.janelia.render;

import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.KeyStrokeAdder.Factory;
import org.scijava.ui.behaviour.util.Actions;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.scijava.ui.behaviour.util.InputActionBindings;

import com.opencsv.CSVReader;

import bdv.export.ProgressWriter;
import bdv.export.ProgressWriterConsole;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import bigwarp.BigWarpExporter;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.IntervalView;
import net.imglib2.view.MixedTransformView;
import net.imglib2.view.Views;


public class KDTreeRendererVNC<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-s", aliases = {"--synapsePath"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private String synapsePaths;

		@Option(name = "-r", aliases = {"--radius"}, required = false, usage = "Radius for synapse point spread function")
		private double radius = 20;

		@Option(name = "-p", aliases = {"--n5Path"}, required = false, usage = "N5 path")
		private String n5Path = "";
		
		@Option(name = "-i", aliases = {"--n5Dataset"}, required = false, usage = "N5 image datasets")
		private String dataset = "22-34";

		@Option(name = "-o", aliases = {"--output"}, required = false, usage = "")
		private String output;

		@Option(name = "-e", aliases = {"--resolution"}, required = false, usage = "Output resolution")
		private String resolutionArg;

		@Option(name = "-f", aliases = {"--subsample-pts"}, required = false, usage = "Subsampling factor for points")
		private int subFactor = 1;

		@Option(name = "-t", aliases = {"--threads"}, required = false, usage = "Number of threads")
		private int nThreads = 1;
		
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
		public String getDatasets() {

			return synapsePaths;
		}
		
		/**
		 * @return the synapse file paths
		 */
		public String getSynapsePaths() {

			return synapsePaths;
		}

		/**
		 * @return the group
		 */
		public double getRadius() {

			return radius;
		}
	}

	static final double searchDist = 150;
	static final double searchDistSqr = searchDist * searchDist;
	static final double invSquareSearchDistance = 1.0 / searchDist / searchDist; 
	
	final KDTree< T > tree;
	Interval itvl;

	public KDTreeRendererVNC( List<T> vals, List<P> pts )
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
	
	
	@SuppressWarnings( "unchecked" )
	public static Pair< RealRandomAccessible< DoubleType >, Interval > load( final Options options )
	{
		final String datasetNames = options.getDatasets();
		final int subFactor = options.subFactor;

		// load synapses
		KDTreeRendererRaw< DoubleType, RealPoint > treeRenderer = KDTreeRendererVNC.load( options.getSynapsePaths(), null, subFactor );
		Interval itvl = treeRenderer.getInterval();

		// This works
		RealRandomAccessible< DoubleType > source = treeRenderer.getRealRandomAccessible( options.getRadius(), KDTreeRendererRaw::rbf );
		return new ValuePair( source, itvl );
	}

	public static BdvStackSource< DoubleType > view( final RealRandomAccessible< DoubleType > source, final Interval itvl, final BdvOptions bdvOpts )
	{

		BdvStackSource< DoubleType > bdv = BdvFunctions.show( source, itvl, "tbar render", bdvOpts );
		bdv.setDisplayRange( 0, 800 );
		return bdv;
	}

	public static void main( String[] args ) throws ImgLibException, IOException
	{
		BdvOptions bdvOpts = BdvOptions.options().numRenderingThreads( 2 )
				.preferredSize( 1280, 1024 );

		final Options options = new Options(args);

		double amount = 20;
		//RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>( amount );
		RadiusChange<DoubleType> rc = new RadiusChange<DoubleType>();

		Pair< RealRandomAccessible< DoubleType >, Interval > sourceItvl = load( options );

		if( options.output == null )
		{
			view( sourceItvl.getA(), sourceItvl.getB(), bdvOpts );
		}
		else
		{
			final double[] resolution = Arrays.stream( options.resolutionArg.split( "," ) ).mapToDouble( Double::parseDouble ).toArray();

			// microns
			Scale3D physicalSpacing = new Scale3D( 0.008, 0.008, 0.008 );
			Scale3D outputSpacing = new Scale3D( resolution );

			Scale3D renderXfm = physicalSpacing.copy();
			renderXfm.preConcatenate( outputSpacing.inverse() );

			FinalInterval emInterval = new FinalInterval( 46113, 59467, 82276 );

			final FinalInterval renderInterval = transformRealInterval( renderXfm, emInterval );
			System.out.println( "render Interval: " + renderInterval );

			IntervalView< DoubleType > img = Views.interval( 
					Views.raster( RealViews.affine( sourceItvl.getA(), renderXfm ) ),
					renderInterval );
			
			
			RandomAccessibleInterval< DoubleType > resultImg = copyToImageStackIterOrder( 
					img,
					renderInterval,
					new ArrayImgFactory<>( new DoubleType() ),
					options.nThreads,
					new ProgressWriterConsole() );

			ImagePlus imp = ImageJFunctions.wrap( resultImg, "rendered" );
			imp.getCalibration().pixelWidth = resolution[ 0 ];
			imp.getCalibration().pixelHeight = resolution[ 1 ];
			imp.getCalibration().pixelDepth = resolution[ 2 ];
			imp.getCalibration().setUnit( "um" );

			System.out.println( "saving" );
			IJ.save( imp, options.output );
			System.out.println( "done" );
		}

//		InputTriggerConfig trigConfig = bdv.getBdvHandle().getViewerPanel().getOptionValues().getInputTriggerConfig();
//		if( trigConfig == null )
//			trigConfig = new InputTriggerConfig();
//		
//		RKActions.installActionBindings( bdv.getBdvHandle().getKeybindings(), bdv.getBdvHandle().getViewerPanel(), 
//				rc, trigConfig );


//		recordMovie( bdv.getBdvHandle().getViewerPanel() );
	}
	
	public static FinalInterval transformRealInterval( RealTransform xfm, RealInterval interval )
	{
		int nd = interval.numDimensions();
		double[] pt = new double[ nd ];
		double[] ptxfm = new double[ nd ];

		long[] min = new long[ nd ];
		long[] max = new long[ nd ];

		// transform min		
		for( int d = 0; d < nd; d++ )
			pt[ d ] = interval.realMin( d );
		
		xfm.apply( pt, ptxfm );
		copyToLongFloor( ptxfm, min );


		// transform max
		
		for( int d = 0; d < nd; d++ )
		{
			pt[ d ] = interval.realMax( d );
		}
		
		xfm.apply( pt, ptxfm );
		copyToLongCeil( ptxfm, max );
		
		return new FinalInterval( min, max );
	}

	public static void copyToLongFloor( final double[] src, final long[] dst )
	{
		for( int d = 0; d < src.length; d++ )
			dst[ d ] = (long)Math.floor( src[d] );
	}

	public static void copyToLongCeil( final double[] src, final long[] dst )
	{
		for( int d = 0; d < src.length; d++ )
			dst[ d ] = (long)Math.floor( src[d] );
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
		final KDTreeRendererVNC<T,?> kdtr;
		final double amount;
		public RBFRealRandomAccessible( KDTreeRendererVNC<T,?> kdtr, 
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

	public static KDTreeRendererRaw<DoubleType,RealPoint> load( final String locationCsvF, final String confCsvF, int subFactor )
	{

		boolean success = false;
		KDTreeRendererRaw<DoubleType,RealPoint> treeRenderer = null;
		Interval itvl;
		try
		{
			System.out.println( "locationCsvF: " + locationCsvF);
			CSVReader reader = new CSVReader( new FileReader( locationCsvF ));
			List< String[] > lines = reader.readAll();
			reader.close();

			long[] min = new long[ 3 ];
			long[] max = new long[ 3 ];
			Arrays.fill( min, Long.MAX_VALUE );
			Arrays.fill( max, Long.MIN_VALUE );

			final ArrayList< RealPoint > pts = new ArrayList<>();
			for( int i = 0; i < lines.size(); i += subFactor )
			{
				final double x = Double.parseDouble( lines.get( i )[ 0 ] );
				final double y = Double.parseDouble( lines.get( i )[ 1 ] );
				final double z = Double.parseDouble( lines.get( i )[ 2 ] );

				double[] p = new double[] { x, y, z };
				pts.add( new RealPoint( p ));

				for( int d = 0; d < 3; d++ )
				{
					long minlong = (long)Math.floor( p[ d ] );
					long maxlong = (long)Math.ceil( p[ d ] );
					if( min[ d ] > minlong )
						min[ d ] = minlong;

					if( max[ d ] < maxlong )
						max[ d ] = maxlong;
				}
			}
			
			lines = null;
			System.gc();

			List< DoubleType > vals = null;
			if( confCsvF == null )
				vals = Stream.iterate( new DoubleType( 1 ), x -> x ).limit( pts.size() ).collect( Collectors.toList() );


			itvl = new FinalInterval( min, max );
			System.out.println( "" + itvl );
			
			treeRenderer = new KDTreeRendererRaw<DoubleType,RealPoint>( vals, pts );
			treeRenderer.setInterval( itvl );

			success = true;
		}
		catch(Exception e)
		{
			System.out.println( "ERROR" );
			e.printStackTrace();
		}
		
		if( !success )
			System.err.println( "Could not read synapses/tbars - returning null" );
		
		return treeRenderer;
	}
	
	public static < T extends NumericType<T> > RandomAccessibleInterval<T> copyToImageStackIterOrder( 
			final RandomAccessible< T > raible,
			final Interval itvl,
			final ImgFactory<T> factory,
			final int nThreads,
			final ProgressWriter progress )
	{
		// create the image plus image
		Img< T > target = factory.create( itvl );
		return copyToImageStackIterOrder( raible, itvl, target, nThreads, progress );
	}

	public static < T extends NumericType<T> > RandomAccessibleInterval<T> copyToImageStackIterOrder( 
			final RandomAccessible< T > ra,
			final Interval itvl,
			final RandomAccessibleInterval<T> target,
			final int nThreads,
			final ProgressWriter progress )
	{
		progress.setProgress(0.0);
		// TODO I wish I didn't have to do this inside this method..
		// 	Maybe I don't have to, and should do it where I call this instead?

		ExecutorService threadPool = Executors.newFixedThreadPool( nThreads );

		LinkedList<Callable<Boolean>> jobs = new LinkedList<Callable<Boolean>>();
		for( int i = 0; i < nThreads; i++ )
		{

			final int offset = i;
			jobs.add( new Callable<Boolean>()
			{
				public Boolean call()
				{
					try
					{
						IterableInterval<T> it = Views.flatIterable( target );
						final RandomAccess< T > access = ra.randomAccess();

						long N = it.size();
						final Cursor< T > c = it.cursor();
						c.jumpFwd( 1 + offset );
						for( long j = offset; j < N; j += nThreads )
						{
							access.setPosition( c );
							c.get().set( access.get() );
							c.jumpFwd( nThreads );
							
							if( offset == 0  && j % (nThreads * 100000) == 0 )
							{
								double ratio = 1.0 * j / N;
								progress.setProgress( ratio ); 
							}
						}

						return true;
					}
					catch( Exception e )
					{
						e.printStackTrace();
					}
					return false;
				}
			});
		}
		try
		{
			threadPool.invokeAll( jobs );
			threadPool.shutdown(); // wait for all jobs to finish

		}
		catch ( InterruptedException e1 )
		{
			e1.printStackTrace();
		}

		progress.setProgress(1.0);
		return target;
	}
	
}