package org.janelia.render;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import net.imglib2.RealLocalizable;
import net.imglib2.exception.ImgLibException;
import net.imglib2.type.numeric.RealType;

public class SynapseJsonXfmFlyemIncEZ<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		private String n5Path = "/nrs/flyem/data/tmp/Z0115-22.n5";

		@Option(name = "-i", aliases = {"--input"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private List<String> synapsePaths;

		@Option(name = "-o", aliases = {"--output"}, required = true, usage = "output synapse json files")
		private List<String> outputs;
		
		@Option(name = "-n", required = true, usage = "slab number [22,34]")
		private List<Integer> slabNumbers;

		private boolean parsedSuccessfully;
		
		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
				parsedSuccessfully = synapsePaths.size() > 0 && synapsePaths.size() == slabNumbers.size() && synapsePaths.size() == outputs.size();
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
		 * @return the transform top dataset names
		 */
		public List<Integer> getSlabNumbers() {

			return slabNumbers;
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

	}

	public static List<Long> topOffsetsGary( List<Integer> slabnums )
	{
		final HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		map.put( 22,   20 );
		map.put( 23, 1109 );
		map.put( 24,   20 );
		map.put( 25, 1152 );
		map.put( 26, 2154 );
		map.put( 27, 1624 );
		map.put( 28,   20 );
		map.put( 29,   20 );
		map.put( 30,   20 );
		map.put( 31,   20 );
		map.put( 32,   20 );
		map.put( 33,   20 );
		map.put( 34,   20 );

		return slabnums.stream().map( x -> new Long( map.get( x )) ).collect( Collectors.toList() );
	}
	
	public static List<Long> botOffsetsGary( List<Integer> slabnums )
	{
		final HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		map.put( 22, 2086 );
		map.put( 23, 3725 );
		map.put( 24, 2909 );
		map.put( 25, 3963 );
		map.put( 26, 4990 );
		map.put( 27, 4338 );
		map.put( 28, 2613 );
		map.put( 29, 2685 );
		map.put( 30, 2648 );
		map.put( 31, 2699 );
		map.put( 32, 2688 );
		map.put( 33, 2615 );
		map.put( 34, 2674 );

		return slabnums.stream().map( x -> new Long( map.get( x )) ).collect( Collectors.toList() );
	}
	
	public static List<Long> topOffsetsStephan( List<Integer> slabnums )
	{
		final HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		map.put( 22, 20 );
		map.put( 23,  4 );
		map.put( 24, 20 );
		map.put( 25,  2 );
		map.put( 26,  4 );
		map.put( 27,  0 );
		map.put( 28, 20 );
		map.put( 29, 20 );
		map.put( 30, 20 );
		map.put( 31, 20 );
		map.put( 32, 20 );
		map.put( 33, 20 );
		map.put( 34, 20 );

		return slabnums.stream().map( x -> new Long( map.get( x )) ).collect( Collectors.toList() );
	}
	
	public static List<Long> botOffsetsStephan( List<Integer> slabnums )
	{
		final HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		map.put( 22, 2086 );
		map.put( 23, 2620 );
		map.put( 24, 2909 );
		map.put( 25, 2813 );
		map.put( 26, 2840);
		map.put( 27, 2714 );
		map.put( 28, 2613 );
		map.put( 29, 2685 );
		map.put( 30, 2648 );
		map.put( 31, 2699 );
		map.put( 32, 2688 );
		map.put( 33, 2615 );
		map.put( 34, 2674 );

		return slabnums.stream().map( x -> new Long( map.get( x )) ).collect( Collectors.toList() );
	}
	
	public static List<String> offsetDataNames( List<Integer> slabnums, String topOrBot ) 
	{
		return slabnums.stream()
				.map( x -> ("/align-13/slab-"+x+"."+topOrBot+".face") )
				.collect( Collectors.toList()); 
	}
	
	public static void main( String[] args ) throws ImgLibException, IOException
	{

		final Options options = new Options(args);
		if( !options.parsedSuccessfully )
			return;
		
		List<Long> topOffsets = topOffsetsGary( options.getSlabNumbers() );
		List<Long> botOffsets = botOffsetsGary( options.getSlabNumbers() );
		
		List<String> topDatasetNames = offsetDataNames( options.getSlabNumbers(), "top" );
		List<String> botDatasetNames = offsetDataNames( options.getSlabNumbers(), "bot" );

		System.out.println( "n5path : " + options.getN5Path() );
		System.out.println( " " );
		System.out.println( "inputs : " + options.getSynapsePaths() );
		System.out.println( "outputs : " + options.getOutputs() );
		
		System.out.println (" " );
		System.out.println( "top offsets : " + topOffsets );
		System.out.println( "bot offsets : " + botOffsets );
		System.out.println( "top data    : " + topDatasetNames );
		System.out.println( "bot data    : " + botDatasetNames );
		
		SynapseJsonXfmFlyemInc.run(
				options.getN5Path(),
				options.getSynapsePaths(),
				options.getOutputs(),
				topOffsets,
				botOffsets,
				topDatasetNames,
				botDatasetNames,
				true );
	 }

}
