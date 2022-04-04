package org.janelia.maleBrain;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.render.KDTreeRendererRaw;
import org.janelia.saalfeldlab.transform.io.TransformReader;

import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import io.IOHelper;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.parallel.SequentialExecutorService;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealTransformRandomAccessible;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import process.RenderTransformed;

public class VisMaleBrain implements Callable<Void>
{
	
	final String emRawN5 = "/nrs/flyem/render/n5/Z0720_07m_BR";
	final String emRawDataset = "/40-06-final";

	final String emClaheN5 = "/nrs/flyem/render/n5/Z0720_07m_BR/40-06-dvid-coords-clahe-from-jpeg.n5";
	final String emClaheDataset = "/grayscale";

	String templatePath = "/groups/saalfeld/public/jrc2018/JRC2018_MALE_40x.nrrd";
	String synapsesPath = "/nrs/saalfeld/john/flyem_maleBrain/tbars/render_tbars_512nm.nrrd";

	String fwdDfieldPath = "/nrs/saalfeld/john/flyem_maleBrain/regexps/exp0009_script_0019.sh_20220328163849/result_0019/dfield.nrrd";
	String invDfieldPath = null;

	String templateClaheSpacePath = "/nrs/saalfeld/john/flyem_maleBrain/regexps/exp0029_t9_0029.sh_20220330165846/result_0029/JRC2018M_xfm_v2.nrrd";

	boolean showRaw = false;
	boolean showClahe = false;

	private boolean showTemplate = false;
	private boolean showTemplateOnEm = false;

	private boolean showSynapses = false;

	public VisMaleBrain() { }

	public static void main( String[] args ) throws IOException
	{
		System.out.println( "start");
		VisMaleBrain vis = new VisMaleBrain();
		
//		vis.showRaw = true;
		vis.showClahe = true;
		vis.showSynapses = true;
//		vis.showTemplate = true;
		vis.showTemplateOnEm = true;

		vis.call();
	}

	public Void call() throws IOException
	{
		BdvStackSource<?> bdv = null;
		BdvOptions opts = BdvOptions.options().numRenderingThreads(16);

		if( showRaw )
			opts = opts.addTo( KDTreeRendererRaw.loadImages(
					emRawN5, 
					Collections.singletonList( emRawDataset ),
					rawTransform(), 
					new FinalVoxelDimensions("nm", 8, 8, 8 ), 
					true, 
					bdv,
					opts));
		
		if( showClahe )
			opts = opts.addTo( KDTreeRendererRaw.loadImages(
					emClaheN5, 
					Collections.singletonList( emClaheDataset ),
					claheTransform(), 
					new FinalVoxelDimensions("nm", 8, 8, 8 ), 
					true, 
					bdv,
					opts));
		
		if( showTemplate )
		{
			System.out.println( "show template");
			bdv = loadImgTransform( templatePath, null, false, opts );
			opts = opts.addTo ( bdv );
		}

		if( showTemplateOnEm )
		{
			System.out.println( "show template on EM");
			bdv = loadImgTransform( templateClaheSpacePath, null, true, opts );
			opts = opts.addTo ( bdv );
		}

		if( showSynapses )
		{
			System.out.println( "show synapses");
//			bdv = loadImgTransform( synapsesPath, fwdDfieldPath, true, opts );
			bdv = loadImgTransform( synapsesPath, null, true, opts );
			opts = opts.addTo ( bdv );
		}

		return null;
	}

	public AffineTransform3D rawTransform()
	{
		final AffineTransform3D fwd = new AffineTransform3D();
		fwd.set(0.0, 0.0, 1.0, 1772, 
				0.0, 1.0, 0.0, -2304, 
				1.0, 0.0, 0.0, 768 );

		final AffineTransform3D xfm = fwd.inverse();
		xfm.preConcatenate( new Scale3D( 8, 8, 8 ));
		return xfm;
	}

	public Scale3D umToNm() {
		return new Scale3D( 1000, 1000, 1000 );
	}
	
	public AffineTransform3D claheTransform()
	{
		final AffineTransform3D xfm = new AffineTransform3D();
		xfm.preConcatenate( new Scale3D( 8, 8, 8 ));
		return xfm;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public <T extends RealType<T> & NativeType<T>> BdvStackSource loadImgTransform(
			String imagePath,
			String transformPath,
			boolean toNm,
			BdvOptions opts) {

		InvertibleRealTransform dfield = null;
		if( transformPath != null )
		{
			System.out.println( "loading transform: " + transformPath );
			dfield = TransformReader.readInvertible(transformPath);
		}


		IOHelper io = new IOHelper();
		RandomAccessibleInterval<T> templateImg = io.readRai(imagePath);

		Scale3D res = new Scale3D(io.getResolution());

		if( toNm )
			res.preConcatenate( umToNm() );

		RealTransformSequence seq = new RealTransformSequence();
		if( transformPath != null )
			seq.add(dfield);

		seq.add(res.inverse());

		
		RealRandomAccessible xfmImg = new RealTransformRandomAccessible( 
				io.interpolateExtend(templateImg, "LINEAR", "0"),
				seq );
		
//		RealPoint tmpPt = new RealPoint( Intervals.maxAsDoubleArray(templateImg));
//		seq.apply(tmpPt, tmpPt);
//		FinalInterval xfmItvl = new FinalInterval( 
//				Arrays.stream( tmpPt.positionAsDoubleArray() ).mapToLong( x -> (long)x).toArray());

		return BdvFunctions.show( xfmImg, templateImg, new File( imagePath ).getName(), opts );
	}
	

}