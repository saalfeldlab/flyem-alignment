package org.janelia.render;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleUnaryOperator;

import org.janelia.render.TbarPrediction.TbarCollection;
import org.janelia.saalfeldlab.hotknife.ViewAlignedSlabSeries.Options;
import org.janelia.saalfeldlab.hotknife.util.Grid;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bigwarp.BigWarpExporter;
import ij.IJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.neighborsearch.RadiusNeighborSearch;
import net.imglib2.neighborsearch.RadiusNeighborSearchOnKDTree;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ClippedTransitionRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class KDTreeRenderer<P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

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
	
	public KDTreeRenderer( List<DoubleType> vals, List<P> pts )
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


		AffineTransform3D permuteYZ= new AffineTransform3D();
		permuteYZ.set(
				1.0, 0.0, 0.0, 0.0, 
				0.0, 0.0, 1.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );

		int sx = 100;
		int sy = 100;
		int sz = 100;
		AffineTransform3D scale = new AffineTransform3D();
		scale.set(
				1.0/sx, 0.0, 0.0, 0.0, 
				0.0, 1.0/sy, 0.0, 0.0,
				0.0, 0.0, 1.0/sz, 0.0 );

		long zOffset = 0;
		
		BdvStackSource<?> bdv = null;
		
		for (int i = 0; i < datasetNames.size(); ++i) {
//		for (int i = 0; i < 1; ++i) {

			// load synapses
			TbarCollection tbars = TbarPrediction.loadAll( options.getSynapsePaths().get( i ) );
			System.out.println( tbars );
			KDTreeRenderer<RealPoint> treeRenderer = new KDTreeRenderer<RealPoint>( tbars.getValues( 0 ), tbars.getPoints() );

			RealRandomAccessible< DoubleType > source= treeRenderer.getRealRandomAccessible( 
					options.getRadius(),
					KDTreeRenderer::rbf );
			
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

			final FinalInterval cropInterval = new FinalInterval(
					new long[] { fMin[0] / sx, fMin[1] / sy, ( options.getTopOffsets().get(i) + zOffset ) / sz },
					new long[] { fMax[0] / sx, fMax[1] / sy, ( options.getBotOffsets().get(i) + zOffset ) / sz });

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

			opts = opts.addTo( bdv );

			bdv = BdvFunctions.show( transformedSource, cropInterval, "tbar render", opts );
//			bdv.getSources().get(0).getSpimSource().getInterpolatedSource( 0, 0, null ).realRandomAccess();
			bdv.setDisplayRange( 0, 500 );

			zOffset += options.getBotOffsets().get(i) + 1;
		}
		
	}

}
