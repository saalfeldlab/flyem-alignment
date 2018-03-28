package org.janelia.render;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.janelia.render.SynPrediction.SynCollection;
import org.janelia.render.TbarPrediction.TbarCollection;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.imglib2.RandomAccessibleLoader;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.tools.transformation.TransformedSource;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.ArrayDataAccessFactory;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.cache.ref.SoftRefLoaderCache;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
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

import static net.imglib2.cache.img.AccessFlags.VOLATILE;
import static net.imglib2.cache.img.PrimitiveType.BYTE;
import static net.imglib2.cache.img.PrimitiveType.DOUBLE;
import static net.imglib2.cache.img.PrimitiveType.FLOAT;
import static net.imglib2.cache.img.PrimitiveType.INT;
import static net.imglib2.cache.img.PrimitiveType.LONG;
import static net.imglib2.cache.img.PrimitiveType.SHORT;

public class KDTreeRendererRaw<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-s", aliases = {"--synapsePath"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private List<String> datasets = new ArrayList<>();

		@Option(name = "-r", aliases = {"--radius"}, required = true, usage = "Radius for synapse point spread function")
		private double radius;

		@Option(name = "-p", aliases = {"--n5Path"}, required = false, usage = "N5 path")
		private String n5Path = "";
		
		@Option(name = "-i", aliases = {"--n5Datasets"}, required = false, usage = "N5 image datasets")
		private List<String> images = new ArrayList<>();
		
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
		public List<String> getImages()
		{
			return images;
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

			return datasets;
		}
		
		/**
		 * @return the synapse file paths
		 */
		public List<String> getSynapsePaths() {

			return datasets;
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

	public KDTreeRendererRaw( List<T> vals, List<P> pts )
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
	
	public static void main( String[] args ) throws ImgLibException, IOException
	{
		
		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );
		
		BdvOptions opts = BdvOptions.options().numRenderingThreads( 16 );
		final Options options = new Options(args);

		
		AffineTransform3D transform = toFlyEm;
		if( options.isStephanSpace() )
		{
			System.out.println("Stephan space");
			// load method can save a step if it doesn't have to apply a transform
			// so prefer that over applying the identity
			transform = null;  
		}
		
		
		BdvStackSource<?> bdv = null;
		bdv = loadImages( 
				options.getN5Path(),
				options.getImages(),
				transform,
				new FinalVoxelDimensions("px", new double[]{1, 1, 1}),
				true, bdv );
		
		List< String > datasetNames = options.getDatasets();
		for (int i = 0; i < datasetNames.size(); ++i)
		{
			File synapseFile = new File( options.getSynapsePaths().get( i ));

			// load synapses
			KDTreeRendererRaw<DoubleType,RealPoint> treeRenderer = load( synapseFile.getAbsolutePath() );
			Interval itvl = treeRenderer.getInterval();


			RealRandomAccessible< DoubleType > source = treeRenderer.getRealRandomAccessible( 
					options.getRadius(),
					KDTreeRendererRaw::rbf );

			opts = opts.addTo( bdv );

			bdv = BdvFunctions.show( source, itvl, "tbar render", opts );
//			bdv.getSources().get(0).getSpimSource().getInterpolatedSource( 0, 0, null ).realRandomAccess();
			bdv.setDisplayRange( 0, 500 );
		}


		bdv.viewer.ViewerOptions vo;
		
	}
	
	public static BdvStackSource<?> loadImages( 
			String n5Path,
			List<String> images,
			AffineTransform3D transform,
			VoxelDimensions voxelDimensions,
			boolean useVolatile,
			BdvStackSource<?> bdv ) throws IOException
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

			bdv = mipmapSource( source2render, bdv );
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

		final CachedCellImg<T, ?> img;
		final Cache<Long, Cell<?>> cache =
				new SoftRefLoaderCache().withLoader(LoadedCellCacheLoader.get(grid, loader, type, VOLATILE));

		if (GenericByteType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( BYTE, VOLATILE));
		} else if (GenericShortType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( SHORT, VOLATILE));
		} else if (GenericIntType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( INT, VOLATILE));
		} else if (GenericLongType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( LONG, VOLATILE));
		} else if (FloatType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( FLOAT, VOLATILE));
		} else if (DoubleType.class.isInstance(type)) {
			img = new CachedCellImg(grid, type, cache, ArrayDataAccessFactory.get( DOUBLE, VOLATILE));
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
		final BdvStackSource<?> stackSource = BdvFunctions.show(source, options);
		stackSource.setDisplayRange(0, 255);
		return stackSource;
	}
}