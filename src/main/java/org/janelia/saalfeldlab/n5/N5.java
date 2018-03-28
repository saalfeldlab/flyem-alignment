package org.janelia.saalfeldlab.n5;

import java.io.IOException;

public class N5
{
	public static N5Reader openFSReader( String path )
	{
		try
		{
			return new N5FSReader( path );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}
		return null;
	}
}
