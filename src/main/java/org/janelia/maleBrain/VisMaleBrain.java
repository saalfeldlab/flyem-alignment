package org.janelia.maleBrain;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.janelia.render.KDTreeRendererRaw;

import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.realtransform.AffineTransform3D;

public class VisMaleBrain implements Callable<Void>
{
	
	final String emRawN5 = "/nrs/flyem/render/n5/Z0720_07m_BR/40-06-final";

	final String emClaheN5 = "/nrs/flyem/render/n5/Z0720_07m_BR/40-06-dvid-coords-clahe-from-jpeg.n5/grayscale";

	boolean showRaw = false;
	boolean showClahe = false;

	public VisMaleBrain() { }

	public VisMaleBrain showRaw( boolean showRaw )
	{
		this.showRaw = showRaw;
		return this;
	}

	public VisMaleBrain showClahe( boolean showClahe )
	{
		this.showClahe = showClahe;
		return this;
	}
	
	public AffineTransform3D rawTransform()
	{
		AffineTransform3D xfm = new AffineTransform3D();

		return xfm;
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

		return null;
	}
	
	public static void main( String[] args ) throws IOException
	{
		new VisMaleBrain().showRaw(true).call();
	}

}