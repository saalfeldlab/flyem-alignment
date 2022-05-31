package bigwarp;

import java.io.File;

import javax.swing.ActionMap;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.janelia.saalfeldlab.swc.Swc;

import bdv.export.ProgressWriter;
import bdv.gui.BigWarpViewerOptions;
import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.realtransform.RealTransform;

public class BigWarpSwc< T > extends BigWarp<T>
{

	protected final BigwarpSwcOverlay swcOverlayMoving;

	protected final BigwarpSwcOverlay swcOverlayTarget;

	public BigWarpSwc( bigwarp.BigWarp.BigWarpData< T > data, String windowTitle, BigWarpViewerOptions options, ProgressWriter progressWriter ) throws SpimDataException
	{
		super( data, windowTitle, options, progressWriter );
		

		swcOverlayMoving = new BigwarpSwcOverlay( super.viewerP, null );
		super.viewerP.addGenericOverlay( swcOverlayMoving );
		swcOverlayMoving.setVisible( false );

		swcOverlayTarget = new BigwarpSwcOverlay( super.viewerQ, null );
		super.viewerQ.addGenericOverlay( swcOverlayTarget );
		swcOverlayTarget.setVisible( false );
		

		final ActionMap actionMap = BigWarpSwcActions.createActionMap( this );

		JMenuBar menubar = super.getLandmarkFrame().getJMenuBar();
		JMenuItem swcMenuItem = new JMenuItem( actionMap.get( BigWarpSwcActions.LOAD_SWC_DIALOG ));
		swcMenuItem.setText("Load Swc" );
		menubar.add( swcMenuItem );

		JMenuItem swcOptionsItem = new JMenuItem( actionMap.get( BigWarpSwcActions.SWC_OVERLAY_OPTIONS ));
		swcOptionsItem.setText("Swc Overlay Options" );
		menubar.add( swcOptionsItem );

	}

	public BigWarpSwc( final BigWarpData<T> data, final String windowTitle, final ProgressWriter progressWriter ) throws SpimDataException
	{
		this( data, windowTitle, BigWarpViewerOptions.options( ( detectNumDims( data.sources ) == 2 ) ), progressWriter );
	}
	
	public void loadSwcDialog()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Import swc" );
		gd.addFileField( "swc file", "" );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		String swcPath = gd.getNextString();
		loadSwc( swcPath );
	}
	
	public void loadSwc( final String swcPath, final boolean moving, final RealTransform transform )
	{
		System.out.println( swcPath );

		Swc swc = Swc.read( new File( swcPath ) );
		

		if( swc != null )
		{
			
			if( transform != null )
			{
				
			}
			
			if( moving ) {
				swcOverlayMoving.set( swc );
				swcOverlayMoving.setVisible( true );
			}
			else {
				swcOverlayTarget.set( swc );
				swcOverlayTarget.setVisible( true );
			}

		}
		else
		{
			System.out.println( "WARNING: could not load swc file from: " + swcPath );
		}
		
	}
	
	public static Swc transform( final Swc swc, final RealTransform transform )
	{
		// TODO
		return null;
	}
	
	public void loadSwc( final String swcPath, final boolean moving )
	{
		loadSwc( swcPath, moving, null );
	}

	public void loadSwc( final String swcPath )
	{
		loadSwc( swcPath, true, null );
	}

	public void setSwcVisParams()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "swc overlay options" );
		gd.addNumericField( "radius", swcOverlayMoving.getRadius(), 3 );
		gd.addNumericField( "R", swcOverlayMoving.getColor().getRed(), 3 );
		gd.addNumericField( "G", swcOverlayMoving.getColor().getGreen(), 3 );
		gd.addNumericField( "B", swcOverlayMoving.getColor().getBlue(), 3 );
		gd.addNumericField( "opacity", swcOverlayMoving.getColor().getAlpha(), 3 );
		gd.addCheckbox( "visible", swcOverlayMoving.isVisible() );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return;
		
		double radius = gd.getNextNumber();

		double r = gd.getNextNumber();
		double g = gd.getNextNumber();
		double b = gd.getNextNumber();
		double alpha = gd.getNextNumber();
		
		boolean isVisible = gd.getNextBoolean();
		
		swcOverlayMoving.setVisible( isVisible );
		swcOverlayTarget.setVisible( isVisible );

		swcOverlayMoving.setColor( (int)r, (int)g, (int)b, (int)alpha );
		swcOverlayTarget.setColor( (int)r, (int)g, (int)b, (int)alpha );

		swcOverlayMoving.setRadius( radius );
		swcOverlayTarget.setRadius( radius );

		viewerP.requestRepaint();
		viewerQ.requestRepaint();

	}
	
	public static void main( String[] args )
	{
		BigWarp.main(args);
	}

}
