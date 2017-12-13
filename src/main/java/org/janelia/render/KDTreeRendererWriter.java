package org.janelia.render;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleUnaryOperator;

import org.janelia.render.TbarPrediction.TbarCollection;
import org.janelia.saalfeldlab.hotknife.util.Grid;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import ij.IJ;
import net.imglib2.FinalInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ClippedTransitionRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import net.imglib2.view.composite.CompositeIntervalView;
import net.imglib2.view.composite.RealComposite;

public class KDTreeRendererWriter<P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-o", aliases = { "--outupt" }, required = true, usage = "Output path, e.g. /data-ssd/john/flyem/output_23-27.tif")
		private String outputString = null;
		
		@Option(name = "--n5Path", required = true, usage = "N5 path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private String n5Path = null;

		@Option(name = "-s", aliases = {"--synapsePath"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private List<String> datasets = new ArrayList<>();

		@Option(name = "--transformTopDataset", required = true, usage = "transform top dataset, e.g. /align-22-29/align-7/slab-26.top.face")
		private List<String> transformTopDatasetNames = new ArrayList<>();

		@Option(name = "--transformBotDataset", required = true, usage = "transform bot dataset, e.g. /align-22-29/align-7/slab-26.bot.face")
		private List<String> transformBotDatasetNames = new ArrayList<>();

		@Option(name = "-t", aliases = {"--top"}, required = true, usage = "top slab face offset")
		private List<Long> topOffsets = new ArrayList<>();

		@Option(name = "-b", aliases = {"--bot"}, required = true, usage = "bottom slab face offset")
		private List<Long> botOffsets = new ArrayList<>();

		@Option(name = "-r", aliases = {"--radius"}, required = true, usage = "Radius for synapse point spread function")
		private double radius;

		private boolean parsedSuccessfully;

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
				parsedSuccessfully = datasets.size() == topOffsets.size() && datasets.size() == botOffsets.size();
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}

		/**
		 * @return the output path
		 */
		public String getOutputPath() {

			return outputString;
		}
		
		/**
		 * @return the n5Path
		 */
		public String getN5Path() {

			return n5Path;
		}

		/**
		 * @return the datasets
		 */
		public List<String> getDatasets() {

			return datasets;
		}
		
		/**
		 * @return the transform top dataset names
		 */
		public List<String> getTransformTopDatasetNames() {

			return transformTopDatasetNames;
		}

		/**
		 * @return the transform bot dataset names
		 */
		public List<String> getTransformBotDatasetNames() {

			return transformBotDatasetNames;
		}
		
		/**
		 * @return the synapse file paths
		 */
		public List<String> getSynapsePaths() {

			return datasets;
		}

		/**
		 * @return the top offsets
		 */
		public List<Long> getTopOffsets() {

			return topOffsets;
		}

		/**
		 * @return the bottom offsets (max)
		 */
		public List<Long> getBotOffsets() {

			return botOffsets;
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
	
	final KDTree< DoubleType > tree;
	
	public KDTreeRendererWriter( List<DoubleType> vals, List<P> pts )
	{
		tree = new KDTree< DoubleType >( vals, pts );
	}

	public RealRandomAccessible<DoubleType> getRealRandomAccessible(
			final double searchDist,
			final DoubleUnaryOperator rbf )
	{
		RBFInterpolator.RBFInterpolatorFactory< DoubleType > interp = 
				new RBFInterpolator.RBFInterpolatorFactory< DoubleType >( 
						rbf, searchDist, false, new DoubleType() );

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
		BdvOptions opts = BdvOptions.options().numRenderingThreads( 16 );
		
		final Options options = new Options(args);

		List< String > datasetNames = options.getDatasets();
		
		final N5Reader n5 = N5.openFSReader( options.getN5Path() );

		final String outputPath = options.getOutputPath();

		AffineTransform3D permuteYZ= new AffineTransform3D();
		permuteYZ.set(
				1.0, 0.0, 0.0, 0.0, 
				0.0, 0.0, 1.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );
		
//		int sx = 100;
//		int sy = 100;
//		int sz = 100;
//		AffineTransform3D scale = new AffineTransform3D();
//		scale.set(
//				1.0/sx, 0.0, 0.0, 0.0, 
//				0.0, 1.0/sy, 0.0, 0.0,
//				0.0, 0.0, 1.0/sz, 0.0 );
		
		AffineTransform3D scale = new AffineTransform3D();
		scale.set(
				0.025, 0.0, 0.0, 0.0, 
				0.0, 0.025, 0.0, 0.0,
				0.0, 0.0, 0.025, 0.0 );

		long zOffset = 0;

		BdvStackSource<?> bdv = null;
		ArrayList< RealTransformRandomAccessible<DoubleType,?>> sources = new ArrayList< RealTransformRandomAccessible<DoubleType,?>>();
		
		FinalInterval totalInterval = null;
		
		for (int i = 0; i < datasetNames.size(); ++i) {

			// load synapses
			TbarCollection tbars = TbarPrediction.loadAll( options.getSynapsePaths().get( i ) );
			System.out.println( tbars );
			KDTreeRendererWriter<RealPoint> treeRenderer = new KDTreeRendererWriter<RealPoint>( tbars.getValues( 0 ), tbars.getPoints() );

			RealRandomAccessible< DoubleType > source= treeRenderer.getRealRandomAccessible( 
					options.getRadius(),
					KDTreeRendererWriter::rbf );
			
			String topDatasetName = options.getTransformTopDatasetNames().get( i );
			String botDatasetName = options.getTransformBotDatasetNames().get( i );
			
			final double[] boundsMin = n5.getAttribute( topDatasetName, "boundsMin", double[].class);
			final double[] boundsMax = n5.getAttribute( botDatasetName, "boundsMax", double[].class);

			final long[] fMin = Grid.floorScaled(boundsMin, 1);
			final long[] fMax = Grid.ceilScaled(boundsMax, 1);

			
			// transform 
			final RealTransform top = Transform.loadScaledTransform( n5, topDatasetName );
			final RealTransform bot = Transform.loadScaledTransform( n5, botDatasetName );

			final RealTransform transition =
					new ClippedTransitionRealTransform(
							top,
							bot,
							options.getTopOffsets().get(i),
							options.getBotOffsets().get(i));


			System.out.println( "top off: " + options.getTopOffsets().get(i));
			System.out.println( "bot off: " + options.getBotOffsets().get(i));

			zOffset -= options.getTopOffsets().get(i);
			System.out.println( "zOffset: " + zOffset );
			
//			final FinalInterval cropInterval = new FinalInterval(
//					new long[] {fMin[0], fMin[1], options.getTopOffsets().get(i) + zOffset },
//					new long[] {fMax[0], fMax[1], options.getBotOffsets().get(i) + zOffset });

//			final FinalInterval cropInterval = new FinalInterval(
//					new long[] { fMin[0] / sx, fMin[1] / sy, ( options.getTopOffsets().get(i) + zOffset ) / sz },
//					new long[] { fMax[0] / sx, fMax[1] / sy, ( options.getBotOffsets().get(i) + zOffset ) / sz });

			final FinalInterval cropInterval = new FinalInterval(
					new long[] { (int)Math.round(fMin[0] * scale.get(0,0)), 
							(int)Math.round(fMin[1] * scale.get(1,1)), 
							(int)Math.round(( options.getTopOffsets().get(i) + zOffset ) * scale.get(2,2)) },
					new long[] { (int)Math.round(fMax[0] * scale.get(0,0)), 
							(int)Math.round(fMax[1] * scale.get(1,1)), 
							(int)Math.round( ( options.getBotOffsets().get(i) + zOffset ) * scale.get(2,2)) });

			if( totalInterval == null )
			{
				totalInterval = cropInterval; 
			}
			else
			{
				totalInterval = Intervals.union( totalInterval, cropInterval );
			}
			
			System.out.println( Util.printInterval( cropInterval ));
			
			final AffineTransform3D offset = new AffineTransform3D();
			offset.setTranslation(0, 0, -zOffset);
			
			final RealTransformSequence transformSequence = new RealTransformSequence();
			
			transformSequence.add( scale.inverse() );
			transformSequence.add( offset );
			transformSequence.add( transition );
			transformSequence.add( permuteYZ );

			final RealTransformRandomAccessible<DoubleType,?> transformedSource = new RealTransformRandomAccessible<>(
					source,
					transformSequence );
			
			sources.add( transformedSource );


			zOffset += options.getBotOffsets().get(i) + 1;
		}

		final ArrayList< RandomAccessibleInterval<DoubleType>> stackSources = new ArrayList<>();
		for (final RealTransformRandomAccessible<DoubleType,?> source : sources )
		{
			stackSources.add( Views.interval( Views.raster( source ), totalInterval ) );
		}

		final RandomAccessibleInterval<DoubleType> stack = Views.stack( stackSources );
		final CompositeIntervalView<DoubleType, RealComposite<DoubleType>> compositeStackSources = Views.collapseReal(stack);
		final RandomAccessibleInterval<DoubleType> accumulatedStackSources = Converters.convert(
				compositeStackSources,
				(a, b) -> {
					b.setZero();
					for (int i = 0; i < stackSources.size(); ++i)
						b.add(a.get(i));
				},
				new DoubleType());

		// write everything, slice by slice
		final ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
		final ArrayList<Future<?>> futures = new ArrayList<>();
		final int zeroPadding = Long.toString(accumulatedStackSources.dimension(2)).length();
		for (int z = 0; z < accumulatedStackSources.dimension(2); ++z) {
			final int fz = z;
			futures.add(
					exec.submit(() -> {
						final IntervalView< DoubleType > slice = Views.hyperSlice(accumulatedStackSources, 2, fz);
						final String fileName = String.format("%s/%0" + zeroPadding + "d.tif", outputPath, fz);
						System.out.println(fileName);
						IJ.saveAsTiff(ImageJFunctions.wrap(slice, ""), fileName);
					}));
		}

		futures.forEach( f -> {
			try {
				f.get();
			} catch (final InterruptedException e) {
				e.printStackTrace();
			} catch (final ExecutionException e) {
				e.printStackTrace();
			}
		});

		exec.shutdown();
	}

}
