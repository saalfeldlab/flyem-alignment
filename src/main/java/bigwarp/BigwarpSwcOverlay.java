package bigwarp;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

import org.janelia.saalfeldlab.swc.Swc;
import org.janelia.saalfeldlab.swc.SwcPoint;

import bdv.viewer.BigWarpViewerPanel;
import bdv.viewer.BigWarpViewerSettings;
import bdv.viewer.overlay.BigWarpGenericOverlay;
import net.imglib2.realtransform.AffineTransform3D;

public class BigwarpSwcOverlay extends BigWarpGenericOverlay<Swc>
{
	
	private double radius;
	
	private byte opacity;
	
	private Color color;

	private final double[] globalCoords = new double[ 3 ];

	private final double[] viewerCoords = new double[ 3 ];

	private final double[] viewerCoordsParent = new double[ 3 ];

	private final AffineTransform3D transform = new AffineTransform3D();
	
	public BigwarpSwcOverlay( final BigWarpViewerPanel viewer, Swc swc )
	{
		super( viewer, swc );

		radius = ( Double ) viewer.getSettings().get( 
				BigWarpViewerSettings.KEY_SPOT_SIZE );
		
		color = Color.ORANGE;
		setOpacity( (byte)64 );
	}

	public double getRadius()
	{
		return radius;
	}

	public void setRadius( final double radius )
	{
		this.radius = radius;
	}

	public void setOpacity( final byte a )
	{
		this.opacity = a;
		color = new Color( color.getRed(), color.getGreen(), color.getBlue(), a );
	}
	
	public Color getColor()
	{
		return color;
	}

	public void setColor( int r, int g, int b, int a )
	{
		color = new Color( r, g, b, a );
	}

	public void paint( final Graphics2D g )
	{
		if( obj == null )
			return;

		g.setColor( color );
		g.setStroke( BigWarpViewerSettings.NORMAL_STROKE );

		viewer.getState().getViewerTransform( transform );
		
		final double radiusRatio = ( Double ) viewer.getSettings().get( 
				BigWarpViewerSettings.KEY_SPOT_RADIUS_RATIO );

		List< SwcPoint > pts = obj.getPoints();
		for( SwcPoint pt : pts )
		{
			globalCoords[ 0 ] = pt.x;
			globalCoords[ 1 ] = pt.y;
			globalCoords[ 2 ] = pt.z;
			transform.apply( globalCoords, viewerCoords );
			


			final double zv = viewerCoords[ 2 ];
			final double dz2 = zv * zv;
			final double rad = radius * radiusRatio;

			if ( dz2 < rad * rad )
			{ 
//				System.out.println("PAINTING");
				final double arad = Math.sqrt( rad * rad - dz2 );
				
				// vary size
				g.fillOval( ( int ) ( viewerCoords[ 0 ] - arad ), 
							( int ) ( viewerCoords[ 1 ] - arad ), 
							( int ) ( 2 * arad + 1 ), ( int ) ( 2 * arad + 1) );
				
//				if( pt.previous >= 0)
//				{
//					SwcPoint parentPt = pts.get( pt.previous );
//					globalCoords[ 0 ] = parentPt.x;
//					globalCoords[ 1 ] = parentPt.y;
//					globalCoords[ 2 ] = parentPt.z;
//					transform.apply( globalCoords, viewerCoordsParent );
//
//					g.drawLine( 
//							(int)viewerCoords[ 0 ], (int)viewerCoords[ 1 ], 
//							(int)viewerCoordsParent[ 0 ], (int)viewerCoordsParent[ 1 ] );
//				}
				

			}
			
		}
	}

}
