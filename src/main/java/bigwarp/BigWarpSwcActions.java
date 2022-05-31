package bigwarp;

import java.awt.event.ActionEvent;

import javax.swing.ActionMap;
import javax.swing.InputMap;

import org.scijava.ui.behaviour.KeyStrokeAdder;
import org.scijava.ui.behaviour.util.AbstractNamedAction;

public class BigWarpSwcActions
{
	public static final String LOAD_SWC_DIALOG = "load swc dialog";
	public static final String SWC_OVERLAY_OPTIONS = "swc overlay options";

	public static ActionMap createActionMap( final BigWarpSwc<?> bw )
	{
		final ActionMap actionMap = new ActionMap();

		new LoadSwcAction( bw ).put( actionMap );
		new SwcOverlayOptionsAction( bw ).put( actionMap );

		return actionMap;
	}

	public static InputMap createInputMapViewer( final KeyStrokeAdder.Factory keyProperties )
	{
		final InputMap inputMap = new InputMap();
		final KeyStrokeAdder map = keyProperties.keyStrokeAdder( inputMap );

		map.put( LOAD_SWC_DIALOG, "ctrl C" );

		return inputMap;
	}
	
	public static class LoadSwcAction extends AbstractNamedAction
	{
		private static final long serialVersionUID = 8787190838772379323L;
		BigWarpSwc<?> bw;

		public LoadSwcAction( final BigWarpSwc<?> bw )
		{
			super( LOAD_SWC_DIALOG );
			this.bw = bw;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			System.out.println( "perform load swc" );
			bw.loadSwcDialog();
		}
	}

	public static class SwcOverlayOptionsAction extends AbstractNamedAction
	{
		private static final long serialVersionUID = 1882500361844064761L;
		BigWarpSwc<?> bw;

		public SwcOverlayOptionsAction( final BigWarpSwc<?> bw )
		{
			super( SWC_OVERLAY_OPTIONS );
			this.bw = bw;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			bw.setSwcVisParams();
		}
	}

}
