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
	
	final String emRawN5 = "/nrs/flyem/render/n5/Z0720_07m_BR/40-06-final";

	final String emClaheN5 = "/nrs/flyem/render/n5/Z0720_07m_BR/40-06-dvid-coords-clahe-from-jpeg.n5/grayscale";
	
	String templatePath = null;
	String synapseRenderPath = null;
	String dfieldPath = null;

	boolean showRaw = false;
	boolean showClahe = false;
	private boolean showTemplate = false;
	private boolean showSynapses = false;

	public VisMaleBrain() { }

	public VisMaleBrain showRaw( boolean showRaw ) {
		this.showRaw = showRaw;
		return this;
	}

	public VisMaleBrain showClahe( boolean showClahe ) {
		this.showClahe = showClahe;
		return this;
	}

	public VisMaleBrain showTemplate(boolean showTemplate) {
		this.showTemplate = showTemplate;
		return this;
	}

	public VisMaleBrain showSynapses(boolean showSynapses) {
		this.showSynapses = showSynapses;
		return this;
	}
	
	public AffineTransform3D rawTransform()
	{
		AffineTransform3D xfm = new AffineTransform3D();

		return xfm;
	}

	public <T extends RealType<T> & NativeType<T>> BdvStackSource loadImgTransform(BdvStackSource bdv,
			BdvOptions opts) {

		InvertibleRealTransform dfield = null;
		if( dfieldPath != null )
			dfield = TransformReader.readInvertible(dfieldPath);

		IOHelper io = new IOHelper();
		RandomAccessibleInterval<T> templateImg = io.readRai(templatePath);
		bdv = BdvFunctions.show(templateImg, new File(templatePath).getName(), opts);

		Scale3D res = new Scale3D(io.getResolution());
		RealTransformSequence seq = new RealTransformSequence();
		if( dfieldPath != null )
			seq.add(dfield);

		seq.add(res.inverse());

		
		RealRandomAccessible xfmImg = new RealTransformRandomAccessible( 
				io.interpolateExtend(templateImg, "LINEAR", "border"),
				seq );
		
		RealPoint tmpPt = new RealPoint( Intervals.maxAsDoubleArray(templateImg));
		seq.apply(tmpPt, tmpPt);
		FinalInterval xfmItvl = new FinalInterval( 
				Arrays.stream( tmpPt.positionAsDoubleArray() ).mapToLong( x -> (long)x).toArray());

		return BdvFunctions.show( xfmImg, xfmItvl, "name", opts );
	}
	
	public Void call() throws IOException
	{
		BdvStackSource<?> bdv = null;
		BdvOptions opts = BdvOptions.options();

		if( showRaw )
			KDTreeRendererRaw.loadImages(
					emRawN5, 
					Collections.singletonList( "grayscale" ),
					rawTransform(), 
					new FinalVoxelDimensions("nm", 8, 8, 8 ), 
					true, 
					bdv,
					opts);
		
		if( showClahe )
			KDTreeRendererRaw.loadImages(
					emClaheN5, 
					Collections.singletonList( "grayscale" ),
					rawTransform(), 
					new FinalVoxelDimensions("nm", 8, 8, 8 ), 
					true, 
					bdv,
					opts);
		
		if( showTemplate )
		{
			bdv = loadImgTransform( bdv, opts );
		}

		return null;
	}
	
	public static void main( String[] args ) throws IOException
	{
		System.out.println( "start");
		VisMaleBrain vis = new VisMaleBrain();
		vis.showTemplate(true);

		vis.templatePath = "/home/john/projects/jrc2018/small_test_data/JRC2018_FEMALE_small.nrrd";

		vis.call();
	}

}