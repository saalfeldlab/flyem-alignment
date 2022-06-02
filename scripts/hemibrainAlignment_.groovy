
/*
 * EDIT THESE!
 */
saalfeldPublicPath = "/groups/saalfeld/public";
hemibrainPath = "/nrs/flyem/data/tmp/Z0115-22.export.n5/22-34";
initLandmarksPath = "/groups/saalfeld/public/forHideo/hemiFineTuning/landmarks_20220531.csv";
bigwarpSettings = "/groups/saalfeld/public/forHideo/hemiFineTuning/bigwarp.settings.xml";

skeletonPath = null;
movingPaths = null;
targetPaths = null;



/*
 * DON'T HAVE TO CHANGE ANYTHING BELOW THIS 
 */
templatePath = new File( saalfeldPublicPath, "flyem_hemiBrainAlign/jrc18/antsA/JRC2018_FEMALE_p8um_iso.nrrd").getAbsoluteFile().getCanonicalPath();
hemiPath = new File( saalfeldPublicPath, "flyem_tbars/tbar_render_20190304_reslice.nrrd").getAbsoluteFile().getCanonicalPath();

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


movingNames = new ArrayList();
targetNames = new ArrayList();
movingRaiList = new ArrayList();
targetRaiList = new ArrayList();

RealImgAndInterval jrc18 = loadNrrd( templatePath, totalXfm, new long[]{ 40, 40 ,40 } );
movingRaiList.add( jrc18.get() );
movingNames.add( "jrc2018" );

RealImgAndInterval hemi = loadNrrd( hemiPath, null, null );
targetRaiList.add( hemi.get() );
targetNames.add( "hemibrain tbars" );


System.out.println("movingPaths");
if( movingPaths != null)
{
	for( String moving : movingPaths )
	{

//			RealImgAndInterval< FloatType > thisMoving = loadNrrd( moving, null, new long[]{ 40, 40 ,40 } );
//			movingRaiList.add( thisMoving.get() );

		println("MOVING: loading from: " + moving );
		RealImgAndInterval thisMoving = loadNrrd( moving, totalXfm, new long[]{ 40, 40 ,40 } );
		movingRaiList.add( thisMoving.get() );
		movingNames.add( (new File( moving)).getName() );
	}
}


if( targetPaths != null)
{
	for( String target : targetPaths )
	{
		System.out.println("TARGET: loading from: " + target );
		RealImgAndInterval thisTarget = loadNrrd( target, null, null );
		targetRaiList.add( thisTarget.get() );
		targetNames.add( (new File( target )).getName() );
	}
}

// use any open images as moving images
imgIds = WindowManager.getIDList();
if( imgIds != null ) {
for( int i = 0; i < imgIds.length; i++ )
{
	imp = WindowManager.getImage( imgIds[i]);
	System.out.println("MOVING: loading from: " + imp );
	RealImgAndInterval thisMoving = loadImagePlus( imp, totalXfm, new long[]{ 40, 40 ,40 } );
	movingRaiList.add( thisMoving.get() );
	movingNames.add( imp.getTitle() );
}
}

BigWarpData data = createBigWarpData( 
		movingRaiList, targetRaiList, 
		movingNames,  targetNames );

// add hemi data
final SharedQueue queue = new SharedQueue( 8 );
Source hemiPix = DifferentHemibrainSpaces.loadHemiN5Pix( hemibrainPath, queue );
AffineTransform3D n5ToRegSpace = DifferentHemibrainSpaces.n5ToRenderSpaceMicronsReal();


Source hemiEmSrc = new RenamableSource(
		DifferentHemibrainSpaces.affineSource( hemiPix, n5ToRegSpace ),
		"hemibrain EM" );
BigWarpInit.add( data, hemiEmSrc, 2, 1, false );

//BigWarpInit.createBigWarpData( movingRaiList, targetRaiList )
BigWarpSwc bw = new BigWarpSwc( data, "bigwarp", new ProgressWriterConsole());

println( "setting swc transform to reg space" );
swcTransform = DifferentHemibrainSpaces.n5ToDvid().inverse();
swcTransform.preConcatenate(n5ToRegSpace);
bw.setSwctransform(swcTransform);

if( skeletonPath != null && !skeletonPath.isEmpty())
{
	bw.loadSwc( skeletonPath );
}

if ( initLandmarksPath != null && !initLandmarksPath.isEmpty() )
{
	bw.getLandmarkPanel().getTableModel().load( new File( initLandmarksPath ));
}

if( bigwarpSettings != null )
{
	try { 
		bw.loadSettings( bigwarpSettings );
	}catch( IOException e ) {}
}


def BigWarpData createBigWarpData(
		final List movingRaiList,
		final List targetRaiList,
		List movingNames,
		List targetNames )
{	
	Source[] movingSources = toSources( movingRaiList, movingNames );
	Source[] targetSources = toSources( targetRaiList, targetNames );

	ArrayList allNames = new ArrayList();
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

def loadImagePlus( ImagePlus ip, InvertibleRealTransform xfm, long[] pad )
{
	println("result: " + ip);
	
	double rx = ip.getCalibration().pixelWidth;
	double ry = ip.getCalibration().pixelHeight;
	double rz = ip.getCalibration().pixelDepth;
	
	AffineTransform3D resInXfm = new AffineTransform3D();
	resInXfm.set( 	rx, 0.0, 0.0, 0.0, 
			  		0.0, ry, 0.0, 0.0, 
			  		0.0, 0.0, rz, 0.0 );


	Img a = ImageJFunctions.wrap( ip );
	RealRandomAccessible rra = null;
	FinalInterval interval = null;
	if( xfm == null )
	{
		rra = RealViews.affine( 
				Views.interpolate( 
					Views.extendZero( a ), 
					new NLinearInterpolatorFactory() ),
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
					new NLinearInterpolatorFactory() ),
				totalXfm );

		interval = RenderUtil.transformInterval( totalXfm, a );
		
		if( pad != null )
			interval = Intervals.expand( interval, pad );
	}
	
	return new RealImgAndInterval( interval, rra );
}

def loadNrrd( String path, InvertibleRealTransform xfm, long[] pad )
{
	println("loadNrrd from : " + path);

	Dfield_Nrrd_Reader nr = new Dfield_Nrrd_Reader();
	File hFile = new File( path );
	ImagePlus ip = nr.load( hFile.getParent(), hFile.getName());
	return loadImagePlus( ip, xfm, pad );
}

def toSources( final List raiList, List names )
{
	Source[] out = new Source[ raiList.size() ];
	int i = 0;
	for( RandomAccessibleInterval img : raiList )
	{
		out[ i ] = new RandomAccessibleIntervalSource( img, Util.getTypeFromInterval( img ), names.get( i ) );
		i++;
	}
	return out;
}

/*
 * ONLY IMPORTS BELOW HERE
 */
 
import hemibrain.DifferentHemibrainSpaces;
import ij.WindowManager;
 
import java.io.File;
import java.io.IOException;
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
import net.imglib2.RealImgAndInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.InvertibleRealTransformSequence;
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
import util.RenderUtil
