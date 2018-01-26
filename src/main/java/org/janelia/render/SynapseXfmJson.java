package org.janelia.render;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import org.janelia.render.SynPrediction.SynCollection;
import org.janelia.render.TbarPrediction.TbarCollection;
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
import net.imglib2.FinalInterval;
import net.imglib2.KDTree;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ClippedTransitionRealTransform;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class SynapseXfmJson<T extends RealType<T>,P extends RealLocalizable>
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

	}

	
	static final double searchDist = 150;
	static final double searchDistSqr = searchDist * searchDist;
	static final double invSquareSearchDistance = 1.0 / searchDist / searchDist; 


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
		String outdir = "/data-ssd/john/flyem/transformed_synapses";
		
		for (int i = 0; i < datasetNames.size(); ++i) {
//		for (int i = 0; i < 1; ++i) {

			File synapseFile = new File( options.getSynapsePaths().get( i ));
			
			String name = synapseFile.getName();
			File outFile = new File( outdir + File.separator + name.replaceAll( ".json", "_xfm.json" ));
			
			
			// load synapses
			List<RealPoint> ptList;
			List< DoubleType > values;
			
			boolean newSynapseJson = false; 
			if(  synapseFile.getName().startsWith( "cx" ))
			{
				System.out.println("loading synapses new");
				SynCollection<DoubleType> synapses = SynPrediction.loadAll( synapseFile.getAbsolutePath(), new DoubleType() );
				System.out.println( synapses );

				System.out.println( "outFile: " + outFile );

				newSynapseJson = true;
				ptList = synapses.getPoints();
				values = synapses.getValues();
			}
			else
			{
				System.out.println("loading synaptbarsses old");
				TbarCollection<DoubleType> tbars = TbarPrediction.loadAll( options.getSynapsePaths().get( i ), new DoubleType() );
				
				outFile = new File( outdir + File.separator + name.replaceAll( ".json", i + "_xfm.json" ));
				System.out.println( "outFile: " + outFile );
				
				System.out.println( tbars );
				ptList = tbars.getPoints();
				values = tbars.getValues();
			}

			String topDatasetName = options.getTransformTopDatasetNames().get( i );
			String botDatasetName = options.getTransformBotDatasetNames().get( i );

			// transform 
			final RealTransform top = Transform.loadScaledTransform( n5, topDatasetName );
			final RealTransform bot = Transform.loadScaledTransform( n5, botDatasetName );

			final RealTransform transition =
					new ClippedTransitionRealTransform(
							top,
							bot,
							options.getTopOffsets().get(i),
							options.getBotOffsets().get(i));

			final AffineTransform3D offset = new AffineTransform3D();
			offset.setTranslation(0, 0, -zOffset);
			
			System.out.println("no inverse permutation");
			final RealTransformSequence transformSequence = new RealTransformSequence();
//			transformSequence.add( permuteYZ.inverse() );
			transformSequence.add( offset );
			transformSequence.add( transition );
			transformSequence.add( permuteYZ );

			
			List< RealPoint > pts_xfm = TbarPrediction.transformPoints( ptList, transformSequence );
			SynCollection< DoubleType > syns_xfm = new SynCollection< DoubleType >( pts_xfm, values, new DoubleType() );
			SynPrediction.write( syns_xfm, outFile.getAbsolutePath() );


			zOffset += options.getBotOffsets().get(i) + 1;
		}
		
	}

}
