package org.janelia.render;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import ij.IJ;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.KDTree;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.exception.ImgLibException;
import net.imglib2.img.imageplus.FloatImagePlus;
import net.imglib2.img.imageplus.ImagePlusImgs;
import net.imglib2.interpolation.neighborsearch.RBFInterpolator;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

public class KDTreeRendererCsv<T extends RealType<T>,P extends RealLocalizable>
{

	@SuppressWarnings("serial")
	public static class Options implements Serializable {

		@Option(name = "-i", aliases = {"--input"}, required = true, usage = "Input point ")
		private String inputPath;

		@Option(name = "-o", aliases = {"--output"}, required = false, usage = "Output image path")
		private String output;

		@Option(name = "-d", aliases = {"--delimiter"}, required = false, usage = "Delimiter")
		private String delimiter = ",";

		@Option(name = "-p", aliases = {"--psf-radius"}, required = true, usage = "Radius for synapse point spread function")
		private double radius;

		@Option(name = "-r", aliases = {"--output-resolution"}, required = true, usage = "Resolution of output image")
		private String resolution;

		@Option(name = "-ir", aliases = {"--input-resolution"}, required = false, usage = "Resolution of input points")
		private String inputResolution;

		@Option(name = "-s", aliases = {"--size"}, required = true, usage = "Output image size")
		private String sizeString;

		public Options(final String[] args) {

			final CmdLineParser parser = new CmdLineParser(this);
			try {
				parser.parseArgument(args);
			} catch (final CmdLineException e) {
				System.err.println(e.getMessage());
				parser.printUsage(System.err);
			}
		}
	}

	private double searchDist;

	private double searchDistSqr;

	private double invSquareSearchDistance; 

	final KDTree< T > tree;

	public KDTreeRendererCsv( List<T> vals, List<P> pts, double searchDist )
	{
		tree = new KDTree< T >( vals, pts );
		setSearchDist( searchDist );
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
	
	public void setSearchDist( final double searchDist )
	{
		this.searchDist = searchDist;
		searchDistSqr = searchDist * searchDist;
		invSquareSearchDistance = 1.0 / (searchDist * searchDist); 
	}

	public static double rbf( final double rsqr, final double dSqr, 
			final double invDSqr )
	{
		if( rsqr > dSqr )
			return 0;
		else
			return 50 *( 1 - ( rsqr * invDSqr ));
	}
	
	public static void main( String[] args ) throws ImgLibException, IOException
	{
		final Options options = new Options( args );

		final double[] res = Arrays.stream( options.resolution.split( "," ) ).mapToDouble( Double::parseDouble ).toArray();

		final Scale inputScale;
		if( options.inputResolution == null )
		{
			inputScale = new Scale( new double[] { 1, 1, 1 });
		}
		else
		{
			final double[] ptres = Arrays.stream( options.inputResolution.split( "," ) )
					.mapToDouble( Double::parseDouble ).toArray();
			inputScale = new Scale( ptres );
		}

		final AffineTransform3D scale = new AffineTransform3D();
		scale.set( res[ 0 ], 0.0, 0.0, 0.0, 0.0, res[ 1 ], 0.0, 0.0, 0.0, 0.0, res[ 2 ], 0.0 );

		final List< String > lines = Files.readAllLines( Paths.get( options.inputPath ) );

		final List< RealPoint > pts = lines.stream().map( x -> x.split( options.delimiter ) )
				.map( x -> strToDouble( x ) )
				.map( RealPoint::wrap )
				.map( x -> {
					inputScale.apply( x, x );	
					return x;
				})
				.collect( Collectors.toList() );

		final List< DoubleType > vals = Stream.iterate( new DoubleType( 1 ), x -> x ).limit( pts.size() )
				.collect( Collectors.toList() );

		// build renderer
		KDTreeRendererCsv< DoubleType, RealPoint > treeRenderer = new KDTreeRendererCsv< DoubleType, RealPoint >( 
				vals, pts, options.radius );

		FinalInterval itvl = new FinalInterval( 
				Arrays.stream( options.sizeString.split( "," ) )
					.mapToLong( Long::parseLong ).toArray() );

		final double dSqr = treeRenderer.searchDistSqr;
		final double idSqr = treeRenderer.invSquareSearchDistance;
		RealRandomAccessible< DoubleType > source = treeRenderer.getRealRandomAccessible( 
				options.radius, x -> rbf(x, dSqr, idSqr ));

		final RealRandomAccessible< DoubleType > transformedSource = 
				RealViews.affine( source, scale.inverse() );

		IntervalView< DoubleType > img = Views.interval( Views.raster( transformedSource ), itvl );

		IJ.save( copyToImagePlus( img ), options.output );
	}

	public static double[] strToDouble( final String[] s )
	{
		final double[] out = new double[ s.length ];
		for( int i = 0; i < s.length; i++ )
			out[ i ] = Double.parseDouble( s[ i ]);

		return out;
	}

	public static <T extends RealType<T>> ImagePlus copyToImagePlus( 
			final RandomAccessibleInterval< T > img )
	{
		final FloatImagePlus< FloatType > outImg = ImagePlusImgs.floats( Intervals.dimensionsAsLongArray( img ) );
		LoopBuilder.setImages( img, outImg ).forEachPixel( (x,y) -> y.setReal( x.getRealDouble() ));
		return outImg.getImagePlus();
	}

}
