package hemibrain;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import bdv.export.ProgressWriterConsole;
import bdv.img.RenamableSource;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import bigwarp.BigWarpInit;
import bigwarp.BigWarpSwc;
import bigwarp.BigWarp.BigWarpData;
import ij.ImagePlus;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealImgAndInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import process.RenderTransformed;
import sc.fiji.io.Dfield_Nrrd_Reader;
import util.RenderUtil;

public class Hemi2JRC18F_tweakElastix_opts implements Callable<Void>
{
	@Option( names = { "-m", "--moving" }, required = false, description = "Paths to moving images." )
	private String[] movingPaths;

	@Option( names = { "-t", "--target" }, required = false, description = "Paths to target images." )
	private String[] targetPaths;

	@Option( names = { "-s", "--skeleton" }, required = false, description = "Paths to skeleton (swc)." )
	private String skeletonPath;

	@Option( names = { "-l", "--landmarks" }, required = false, description = "Path to initial landmarks." )
	private String initLandmarksPath;

	@Option( names = { "-p", "--saalfeld" }, required = false, description = "Path to saalfeld/public" )
	private String saalfeldPublicPath = "/groups/saalfeld/public";

	@Option( names = { "-h", "--hemibrain" }, required = false, description = "Path to hemibrain" )
	private String hemibrainPath = "/nrs/flyem/data/tmp/Z0115-22.export.n5/22-34";
	
	public static void main( String[] args ) throws Exception
	{
		CommandLine.call( new Hemi2JRC18F_tweakElastix_opts(), args );
	}
	
	public Void call() throws Exception
	{

		System.out.println( saalfeldPublicPath );
		String templatePath = new File( saalfeldPublicPath, "flyem_hemiBrainAlign/jrc18/antsA/JRC2018_FEMALE_p8um_iso.nrrd").getAbsoluteFile().getCanonicalPath();
		String hemiPath = new File( saalfeldPublicPath, "flyem_tbars/tbar_render_20190304_reslice.nrrd").getAbsoluteFile().getCanonicalPath();

		String[] transformList = new String[]{
				new File( saalfeldPublicPath, "flyem_hemiBrainAlign/jrc18_20190304/elastix5InitMask/result/deformationField_inv.nrrd").getAbsoluteFile().getCanonicalPath() ,
				new File( saalfeldPublicPath, "flyem_hemiBrainAlign/jrc18_20190304/preproc/totalAffine_regSpace.mat").getAbsoluteFile().getCanonicalPath()
		};
		
		InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
		for ( String xfmPath : transformList )
		{
			boolean inv = false;
			if( xfmPath.startsWith( "inverse " ))
			{
				inv = true;
				xfmPath = xfmPath.replace( "inverse ", "" );
			}
			totalXfm.add( RenderTransformed.loadTransform( xfmPath, inv ));
		}


		ArrayList<String> movingNames = new ArrayList<>();
		ArrayList<String> targetNames = new ArrayList<>();
		List< RandomAccessibleInterval<?> > movingRaiList = new ArrayList<RandomAccessibleInterval<?>>();
		List< RandomAccessibleInterval<?> > targetRaiList = new ArrayList<RandomAccessibleInterval<?>>();

		RealImgAndInterval< FloatType > jrc18 = loadNrrd( templatePath, totalXfm, new long[]{ 40, 40 ,40 } );
		movingRaiList.add( jrc18.get() );
		movingNames.add( "jrc2018" );

		RealImgAndInterval< FloatType > hemi = loadNrrd( hemiPath, null, null );
		targetRaiList.add( hemi.get() );
		targetNames.add( "hemibrain tbars" );


		System.out.println("movingPaths");
		if( movingPaths != null)
		{
			for( String moving : movingPaths )
			{

	//			RealImgAndInterval< FloatType > thisMoving = loadNrrd( moving, null, new long[]{ 40, 40 ,40 } );
	//			movingRaiList.add( thisMoving.get() );

				System.out.println("MOVING: loading from: " + moving );
				RealImgAndInterval< FloatType > thisMoving = loadNrrd( moving, totalXfm, new long[]{ 40, 40 ,40 } );
				movingRaiList.add( thisMoving.get() );
				movingNames.add( (new File( moving)).getName() );
			}
		}
		
		
		if( targetPaths != null)
		{
			for( String target : targetPaths )
			{
				System.out.println("TARGET: loading from: " + target );
				RealImgAndInterval< FloatType > thisTarget = loadNrrd( target, null, null );
				targetRaiList.add( thisTarget.get() );
				targetNames.add( (new File( target )).getName() );
			}
		}

		BigWarpData data = herecreateBigWarpData( 
				movingRaiList, 
				targetRaiList, 
				movingNames, 
				targetNames );
		
		// add hemi data
		final SharedQueue queue = new SharedQueue( 8 );
		Source< ? > hemiPix = DifferentHemibrainSpaces.loadHemiN5Pix( hemibrainPath, queue );
		AffineTransform3D n5ToRegSpace = DifferentHemibrainSpaces.n5ToRenderSpaceMicronsReal();


		Source hemiEmSrc = new RenamableSource(
				DifferentHemibrainSpaces.affineSource( hemiPix, n5ToRegSpace ),
				"hemibrain EM" );
		BigWarpInit.add( data, hemiEmSrc, 2, 1, false );

		//BigWarpInit.createBigWarpData( movingRaiList, targetRaiList )
		BigWarpSwc bw = new BigWarpSwc( data, "bigwarp", new ProgressWriterConsole());

		final RealTransformSequence swcTransform = new RealTransformSequence();

		AffineTransform3D affine = DifferentHemibrainSpaces.n5ToDvid().inverse();
		affine.preConcatenate(n5ToRegSpace);

		swcTransform.add(affine);
		swcTransform.add(totalXfm);

		bw.setSwctransform(swcTransform);


		if( skeletonPath != null && !skeletonPath.isEmpty())
		{
			bw.loadSwc( skeletonPath );
		}
		
		if ( initLandmarksPath != null && !initLandmarksPath.isEmpty() )
		{
			bw.getLandmarkPanel().getTableModel().load( new File( initLandmarksPath ));
		}

		return null;
	}
	
	public static BigWarpData herecreateBigWarpData(
			final List<RandomAccessibleInterval<?>> movingRaiList,
			final List<RandomAccessibleInterval<?>> targetRaiList,
			List<String> movingNames,
			List<String> targetNames )
	{
//		int numMovingSources = movingRaiList.size();
//		int numTargetSources = targetRaiList.size();
//		return BigWarpInit.createBigWarpData( 
//				new RaiLoader( movingRaiList, ImagePlusLoader.range( 0, numMovingSources )),
//				new RaiLoader( targetRaiList, ImagePlusLoader.range( numMovingSources, numTargetSources )),
//				names );
		
		Source[] movingSources = toSources( movingRaiList, movingNames );
		Source[] targetSources = toSources( targetRaiList, targetNames );

		ArrayList<String> allNames = new ArrayList<String>();
		for( String s : movingNames )
		{
			allNames.add( s );
		}
		for( String s : targetNames )
		{
			allNames.add( s );
		}


		String[] allNamesArray = allNames.toArray( new String[ allNames.size() ] );
		return BigWarpInit.createBigWarpData( movingSources, targetSources, allNamesArray );
	}
	
	public static Source[] toSources( final List< RandomAccessibleInterval<?>> raiList, List<String> names )
	{
		Source<?>[] out = new Source[ raiList.size() ];
		int i = 0;
		for( RandomAccessibleInterval img : raiList )
		{
			out[ i ] = new RandomAccessibleIntervalSource( img, Util.getTypeFromInterval( img ), names.get( i ) );
			i++;
		}

		return out;
	}

	public static <T extends RealType<T> & NativeType<T>> RealImgAndInterval< T > loadNrrd( String path, InvertibleRealTransform xfm, long[] pad )
	{
		System.out.println("loadNrrd from : " + path);

		Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
		File hFile = new File( path );
		ImagePlus ip = nr.load( hFile.getParent(), hFile.getName());

		System.out.println("result: " + ip);
		
		double rx = ip.getCalibration().pixelWidth;
		double ry = ip.getCalibration().pixelHeight;
		double rz = ip.getCalibration().pixelDepth;
		
		AffineTransform3D resInXfm = new AffineTransform3D();
		resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
				  		0.0, ry, 0.0, 0.0, 
				  		0.0, 0.0, rz, 0.0 );


		Img< T > a = ImageJFunctions.wrap( ip );
		RealRandomAccessible< T > rra = null;
		FinalInterval interval = null;
		if( xfm == null )
		{
			rra = RealViews.affine( 
					Views.interpolate( 
						Views.extendZero( a ), 
						new NLinearInterpolatorFactory<T>() ),
					resInXfm );

			interval = RenderUtil.transformInterval( resInXfm, a );
		}
		else
		{
			InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
			totalXfm.add( resInXfm );
			totalXfm.add( xfm );

			rra = RealViews.transform( 
					Views.interpolate( 
						Views.extendZero( a ), 
						new NLinearInterpolatorFactory<T>() ),
					totalXfm );

			interval = RenderUtil.transformInterval( totalXfm, a );
			
			if( pad != null )
				interval = Intervals.expand( interval, pad );
		}
		
		return new RealImgAndInterval<T>( interval, rra );
	}
	
	public static RealImgAndInterval< FloatType > loadNrrdFloat( String path, InvertibleRealTransform xfm, long[] pad )
	{
		System.out.println("loadNrrd from : " + path);

		Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
		File hFile = new File( path );
		ImagePlus ip = nr.load( hFile.getParent(), hFile.getName());

		System.out.println("result: " + ip);
		
		double rx = ip.getCalibration().pixelWidth;
		double ry = ip.getCalibration().pixelHeight;
		double rz = ip.getCalibration().pixelDepth;
		
		AffineTransform3D resInXfm = new AffineTransform3D();
		resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
				  		0.0, ry, 0.0, 0.0, 
				  		0.0, 0.0, rz, 0.0 );


		Img< FloatType > a = ImageJFunctions.wrapFloat( ip );
		RealRandomAccessible< FloatType > rra = null;
		FinalInterval interval = null;
		if( xfm == null )
		{
			rra = RealViews.affine( 
					Views.interpolate( 
						Views.extendZero( a ), 
						new NLinearInterpolatorFactory<FloatType>() ),
					resInXfm );

			interval = RenderUtil.transformInterval( resInXfm, a );
		}
		else
		{
			InvertibleRealTransformSequence totalXfm = new InvertibleRealTransformSequence();
			totalXfm.add( resInXfm );
			totalXfm.add( xfm );

			rra = RealViews.transform( 
					Views.interpolate( 
						Views.extendZero( a ), 
						new NLinearInterpolatorFactory<FloatType>() ),
					totalXfm );

			interval = RenderUtil.transformInterval( totalXfm, a );
			
			if( pad != null )
				interval = Intervals.expand( interval, pad );
		}
		
		return new RealImgAndInterval<FloatType>( interval, rra );
	}
	
}
