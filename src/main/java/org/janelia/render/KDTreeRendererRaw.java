package org.janelia.render;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.imglib2.Interval;
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

public class KDTreeRendererRaw<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-s", aliases = {"--synapsePath"}, required = true, usage = "synapse json files, e.g. /nrs/flyem/data/tmp/slab-22.json")
		private List<String> datasets = new ArrayList<>();

		@Option(name = "-r", aliases = {"--radius"}, required = true, usage = "Radius for synapse point spread function")
		private double radius;

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
		BdvOptions opts = BdvOptions.options().numRenderingThreads( 16 );
		
		final Options options = new Options(args);

		List< String > datasetNames = options.getDatasets();


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

		BdvStackSource<?> bdv = null;
		
		for (int i = 0; i < datasetNames.size(); ++i) {
//		for (int i = 0; i < 1; ++i) {

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

}

