package org.janelia.render;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.janelia.render.SynPrediction.SynCollection;
import org.janelia.render.TbarPrediction.TbarCollection;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.exception.ImgLibException;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ClippedTransitionRealTransform;
import net.imglib2.realtransform.IncrementalInverter;
import net.imglib2.realtransform.PosFieldTransformInverseGradientDescent;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;

public class SynapseJsonXfmFlyemInc<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "--n5Path", required = true, usage = "N5 path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private String n5Path = null;

		@Option(name = "-s", aliases = {"--synapsePath"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private List<String> synapsePaths = new ArrayList<>();

		@Option(name = "-o", aliases = {"--output"}, required = true, usage = "output synapse json files")
		private List<String> outputs = new ArrayList<>();
		
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
				parsedSuccessfully = synapsePaths.size() == topOffsets.size() && synapsePaths.size() == botOffsets.size();
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
		public List<String> getSynapseJsonPaths() {

			return synapsePaths;
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

			return synapsePaths;
		}
		
		/**
		 * @return the output file paths
		 */
		public List<String> getOutputs()
		{
			return outputs;
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

	public static void main( String[] args ) throws ImgLibException, IOException
	{

		final Options options = new Options(args);
		
		List< String > datasetNames = options.getSynapseJsonPaths();
		
		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );

		AffineTransform3D permuteYZ= new AffineTransform3D();
		permuteYZ.set(
				1.0, 0.0, 0.0, 0.0, 
				0.0, 0.0, 1.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );

		run( options.getN5Path(),
				options.getSynapseJsonPaths(),
				options.getOutputs(),
				options.getTopOffsets(),
				options.getBotOffsets(),
				options.getTransformTopDatasetNames(),
				options.getTransformBotDatasetNames()
				);
	 }

	public static final void run(
			String n5Path,
			List<String> synapsePaths,
			List<String> outputList,
			List<Long> topOffsets,
			List<Long> botOffsets,
			List<String> topDatasetNames,
			List<String> botDatasetNames ) throws IOException
	{
		assert( synapsePaths.size() == topOffsets.size() && 
				synapsePaths.size() == botOffsets.size() &&
				synapsePaths.size() == topDatasetNames.size() &&
				synapsePaths.size() == botDatasetNames.size() &&
				synapsePaths.size() == outputList.size());
		
		boolean fixZ = true;
		System.out.println( "fixZ : " + fixZ );
		
		final N5Reader n5 = N5.openFSReader( n5Path );

		final AffineTransform3D toFlyEm = new AffineTransform3D();
		toFlyEm.set(
				0.0, 0.0, -1.0, 34427, 
				1.0, 0.0, 0.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );

		AffineTransform3D permuteYZ= new AffineTransform3D();
		permuteYZ.set(
				1.0, 0.0, 0.0, 0.0, 
				0.0, 0.0, 1.0, 0.0,
				0.0, 1.0, 0.0, 0.0 );

		for (int i = 0; i < synapsePaths.size(); ++i)
		{
			
			File synapseFile = new File( synapsePaths.get( i ));
			File outFile = new File( outputList.get( i ));
			String name = synapseFile.getName();

			// load synapses
			
			
			boolean newSynapseJson = false; 
			PtsAndValues pav = load( synapseFile.getAbsolutePath() );
			List<RealPoint> ptList = pav.ptList;
			List< DoubleType > values = pav.values;
//			if(  synapseFile.getName().startsWith( "cx" ))
//			{
//				System.out.println("loading synapses new");
//				SynCollection<DoubleType> synapses = SynPrediction.loadAll( synapseFile.getAbsolutePath(), new DoubleType() );
//				System.out.println( synapses );
//
//				System.out.println( "outFile: " + outFile );
//
//				newSynapseJson = true;
//				ptList = synapses.getPoints();
//				values = synapses.getValues();
//			}
//			else
//			{
//				System.out.println("loading tbars old");
//				TbarCollection<DoubleType> tbars = TbarPrediction.loadAll( synapseFile.getAbsolutePath(), new DoubleType() );
//
//				System.out.println( "outFile: " + outFile );
//				System.out.println( tbars );
//				ptList = tbars.getPoints();
//				values = tbars.getValues();
//			}
			
			String topDatasetName = topDatasetNames.get( i );
			String botDatasetName = botDatasetNames.get( i );

			// transform 
			final RealTransform top = Transform.loadScaledTransform( n5, topDatasetName );
			final RealTransform bot = Transform.loadScaledTransform( n5, botDatasetName );

			long topOffset = topOffsets.get ( i );
			long botOffset = botOffsets.get ( i );

			// Hard coded interval offsets
			// This came from the boundsMin in: /nrs/flyem/data/tmp/Z0115-22.n5/align-13/attributes.json
			final AffineTransform3D itvl_offset = new AffineTransform3D();
			itvl_offset.set(
					1.0, 0.0, 0.0, -5756.0,
					0.0, 1.0, 0.0, -3374.0,
					0.0, 0.0, 1.0, topOffset );
			
			final RealTransform transition =
					new ClippedTransitionRealTransform(
							top, bot,
							topOffset, botOffset);


			int slabNum = SLAB_ID_FROM_FACE( topDatasetName );

			double zOffset = SLAB_2_ZOFFSETS().get( slabNum );
			AffineTransform3D zOffsetXfm = new AffineTransform3D();
			zOffsetXfm.set( zOffset, 2, 3 );

			System.out.println( "slabNum : " + slabNum );
			System.out.println( "zOffset : " + zOffsetXfm );
			System.out.println( "ioffset : " + itvl_offset );
			
			ArrayList<RealTransform> before = new ArrayList<RealTransform>();
			before.add( permuteYZ.inverse() );
			
			ArrayList<RealTransform> after = new ArrayList<RealTransform>();
			after.add( itvl_offset.inverse() );
			after.add( zOffsetXfm );
			after.add( toFlyEm );

			
			IncrementalInverter inv = new IncrementalInverter( before, transition, after, 0.34 );
			inv.setToleranceIncreaseFactor( 2 );
			inv.setDefaultIndex( 1 );			
			inv.setFixZ( fixZ );
			
			RealPointErrorList pts_xfm = transformPointsWithErrors( ptList, inv );
			
			SynCollection< DoubleType > syns_xfm = new SynCollection< DoubleType >( 
					pts_xfm.pts,
					values,
					pts_xfm.errs,
					new DoubleType() );
			
			System.out.println( "transformed synapses: " + syns_xfm );
			// System.out.println( "fraction failed: " + syns_xfm.fractionFailed() ); 
			
			SynPrediction.write( syns_xfm, outFile.getAbsolutePath() );

		}
	}
	
	/**
	 * Hard-coded map from slab id's to z-offsets 
	 * @return
	 */
	public static final HashMap<Integer,Double> SLAB_2_ZOFFSETS()
	{
		HashMap<Integer,Double> map = new HashMap<Integer,Double>();
		map.put( 22,    0.0 );
		map.put( 23,  2067.0 );
		map.put( 24,  4684.0 );
		map.put( 25,  7574.0 );
		map.put( 26, 10386.0 );
		map.put( 27, 13223.0 );
		map.put( 28, 15938.0 );
		map.put( 29, 18532.0 );
		map.put( 30, 21198.0 );
		map.put( 31, 23827.0 );
		map.put( 32, 26507.0 );
		map.put( 33, 29176.0 );
		map.put( 34, 31772.0 );
		return map;
	}
	
	static final String SLABID_REGEXP = "/align-\\d\\d/slab-(\\d+).*";

	/**
	 * Returns the slabid from a top or bot transformation datasetname 
	 * (e.g. "/align-13/slab-23.top.face"; will return 23 );
	 * 
	 * @param faceDatasetName the dataset name
	 * @return the slabId
	 */
	public static final int SLAB_ID_FROM_FACE( String faceDatasetName )
	{
		Matcher m = Pattern.compile( SLABID_REGEXP ).matcher( faceDatasetName );
		m.find();
		return Integer.parseInt( m.group( 1 ) );
	}

	
	public static RealPointErrorList transformPointsWithErrors(
			final List<RealPoint> pts, final IncrementalInverter xfm )
	{
		RealPointErrorList ptsAndErrs = new RealPointErrorList();
		double[] source = new double[ 3 ];

		int i = 0;
		for( RealPoint p : pts )
		{
			double[] target = new double[ 3 ];
			p.localize( source );
			xfm.apply( source, target );
			ptsAndErrs.add( RealPoint.wrap( target ), xfm.getBestError() );
			
//			if( target[0] < 29743 || 
//				target[0] > 32360 )
//			{
//				System.out.println("p : " + p +
//						" went to : " + Arrays.toString(target));
//			}
			
			i++;
		}
		return ptsAndErrs;
	}
	
	public static class RealPointErrorList
	{
		public final ArrayList<RealPoint> pts;
		public final ArrayList<Double> errs;
		public RealPointErrorList()
		{
			pts = new ArrayList<RealPoint>();
			errs = new ArrayList<Double>();
		}
		public void add( RealPoint pt, double err )
		{
			pts.add( pt );
			errs.add( err );
		}
	}
	
	public static PtsAndValues load( String synapseFilePath )
	{
		boolean success = false;
		PtsAndValues output = null;
		String errNew = "";
		String errOld = "";
		try
		{
			SynCollection<DoubleType> synapses = SynPrediction.loadAll( synapseFilePath, new DoubleType() );
			System.out.println( "tbars: " + synapses );
			output = new PtsAndValues( synapses.getPoints(), synapses.getValues() );
			success = true;
		}
		catch( Exception e )
		{
			System.out.println( "Not a new synapse json" );
			errNew = e.getMessage();
		}
		
		if( success )
			return output;
		
		try
		{
			TbarCollection<DoubleType> tbars = TbarPrediction.loadAll( synapseFilePath, new DoubleType() );
			System.out.println( "tbars: " + tbars );
			output = new PtsAndValues( tbars.getPoints(), tbars.getValues() );
			success = true;
		}
		catch( Exception e )
		{
			System.out.println( "Not an old synapse json" );
			errOld = e.getMessage();
		}
		
		if( !success )
		{
			System.err.println( "Could not read synapses/tbars " );
			System.err.println( "new format error: \n " + errNew );
			System.err.println( "old format error: \n " + errOld );
		}

		return output;
	}
	
	public static class PtsAndValues
	{
		public final List<RealPoint> ptList;
		public final List< DoubleType > values;
		
		public PtsAndValues(
				final List<RealPoint> ptList,
				final List< DoubleType > values )
		{
			this.ptList = ptList;
			this.values = values;
		}
	}
}
