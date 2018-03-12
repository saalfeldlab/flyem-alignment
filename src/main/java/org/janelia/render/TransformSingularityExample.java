/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.janelia.render;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.janelia.saalfeldlab.hotknife.AbstractOptions;
import org.janelia.saalfeldlab.hotknife.util.Grid;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.ClippedTransitionRealTransform;
import net.imglib2.realtransform.IncrementalInverter;
import net.imglib2.realtransform.PosFieldTransformInverseGradientDescent;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.util.Util;

/**
 *
 *
 * @author John Bogovic &lt;bogovicj@janelia.hhmi.org&gt;
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class TransformSingularityExample
{


	@SuppressWarnings("serial")
	public static class Options extends AbstractOptions implements Serializable {

		@Option(name = "--n5Path", required = true, usage = "N5 path, e.g. /nrs/flyem/data/tmp/Z0115-22.n5")
		private String n5Path = null;

		@Option(name = "-i", aliases = {"--n5Dataset"}, required = true, usage = "N5 datasets, e.g. /nrs/flyem/data/tmp/Z0115-22.n5/slab-22/raw")
		private List<String> datasets = new ArrayList<>();

		@Option(name = "-t", aliases = {"--top"}, required = true, usage = "top slab face offset")
		private List<Long> topOffsets = new ArrayList<>();

		@Option(name = "-b", aliases = {"--bot"}, required = true, usage = "bottom slab face offset")
		private List<Long> botOffsets = new ArrayList<>();

		@Option(name = "-j", aliases = {"--n5Group"}, required = true, usage = "N5 group containing alignments, e.g. /nrs/flyem/data/tmp/Z0115-22.n5/align-6")
		private String n5GroupAlign;

		@Option(name = "-c", aliases = {"--topName"}, required = true, usage = "N5 group for top (ceil) transform")
		private String topName;
		
		@Option(name = "-f", aliases = {"--botName"}, required = true, usage = "N5 group for bottom (floor) transform")
		private String botName;
		
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
		
		public boolean isParsedSuccessfully()
		{
			return parsedSuccessfully;
		}

		/**
		 * @return the n5Path
		 */
		public String getN5Path() {

			return n5Path;
		}
		
		/**
		 * @return dataset name for top transform
		 */
		public String getTopName()
		{
			return topName;
		}

		/**
		 * 
		 * @return dataset name for bot transform
		 */
		public String getBotName()
		{
			return botName;
		}
		
		/**
		 * @return the datasets
		 */
		public List<String> getDatasets() {

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
		public String getGroup() {

			return n5GroupAlign;
		}
	}

	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException
	{
		final Options options = new Options(args);

		if( !options.isParsedSuccessfully() )
			return;

		run(
				options.getN5Path(),
				options.getGroup(),
				options.getTopName(),
				options.getBotName(),
				options.getDatasets(),
				options.getTopOffsets(),
				options.getBotOffsets(),
				new FinalVoxelDimensions("px", new double[]{1, 1, 1}) );
	}

	public static void run(
			final String n5Path,
			final String group,
			final String topTransformDatasetName,
			final String botTransformDatasetName,
			final List<String> datasetNames,
			final List<Long> topOffsets,
			final List<Long> botOffsets,
			final VoxelDimensions voxelDimensions ) throws IOException {

		final N5Reader n5 = N5.openFSReader(n5Path);

		for (int i = 0; i < datasetNames.size(); ++i) {

			final RealTransform top = Transform.loadScaledTransform(n5, group + "/" + topTransformDatasetName);
			final RealTransform bot = Transform.loadScaledTransform(n5, group + "/" + botTransformDatasetName);

			final RealTransform transition =
					new ClippedTransitionRealTransform(
							top,
							bot,
							topOffsets.get(i),
							botOffsets.get(i));


			double[] p1 = new double[]{ 35261.0, 5933.0, 112.0 };
			double[] p2 = new double[]{ 35262.0, 5933.0, 112.0 };
			
			double[] q1 = new double[ 3 ];
			double[] q2 = new double[ 3 ];

			transition.apply( p1, q1 );
			transition.apply( p2, q2 );
			
			System.out.println( "p1 " + Arrays.toString( p1 ) +
					"   ->  " + 
					Arrays.toString( q1 ));
			System.out.println( "p2 " + Arrays.toString( p2 ) +
					"   ->  " + 
					Arrays.toString( q2 ));
			
			double[] diffsrc = new double[ 3 ];
			double[] difftgt = new double[ 3 ];
			
			for( int j = 0; j < 3; j++ )
			{
				diffsrc[j] = p2[ j ] - p1[ j ];
				difftgt[j] = q2[ j ] - q1[ j ];
			}
			
			System.out.println( " " );
			System.out.println( " source points are separated by: " + Arrays.toString(diffsrc) );
			System.out.println( " target points are separated by: " + Arrays.toString(difftgt) );
			
		}

	}
	
}

