package org.janelia.render.data;

import java.io.IOException;
import java.util.List;

import org.janelia.render.SynPrediction;
import org.janelia.render.SynPrediction.SynCollection;
import org.janelia.render.SynapseJsonXfmFlyemInc;
import org.janelia.render.SynapseJsonXfmFlyemInc.PtsAndValues;

import net.imglib2.RealPoint;
import net.imglib2.type.numeric.real.DoubleType;

public class SubSynapses
{

	public static void main( String[] args )
	{
		String inpath = args[ 0 ];
		String outpath = args[ 1 ];
		int subfactor = Integer.parseInt( args[ 2 ] );
		
		PtsAndValues pav = SynapseJsonXfmFlyemInc.load( inpath );
		
		subsampleInPlaceRevrm( pav, subfactor );
		
		List<RealPoint> ptList = pav.ptList;
		List< DoubleType > values = pav.values;
		
		
		SynCollection< DoubleType > syns_xfm = new SynCollection< DoubleType >( 
				ptList,
				values,
				null,
				new DoubleType() );
		
		
		 try
		{
			SynPrediction.write( syns_xfm, outpath );
		} catch ( IOException e )
		{
			e.printStackTrace();
		}


	}
	
	public static void subsampleInPlace(
			PtsAndValues pav,
			int factor )
	{
		System.out.println(" before " + 
				pav.ptList.size() + " " + 
				pav.values.size() );
		for( int i = pav.ptList.size()-1; i >=0 ; i-- )
		{
			if( i % factor != 0 )
			{
				pav.ptList.remove( i );
				pav.values.remove( i );
			}
		}
		System.out.println(" after " + 
				pav.ptList.size() + " " + 
				pav.values.size() );
	}
	
	public static void subsampleInPlaceRevrm(
			PtsAndValues pav,
			int factor )
	{
		System.out.println(" before " + 
				pav.ptList.size() + " " + 
				pav.values.size() );
		for( int i = pav.ptList.size()-1; i >=0 ; i-- )
		{
			if( i % factor != 0 )
			{
				pav.ptList.remove( i );
				pav.values.remove( i );
			}
		}
		System.out.println(" after " + 
				pav.ptList.size() + " " + 
				pav.values.size() );
	}
	
	public static void subsampleInPlaceRm(
			PtsAndValues pav,
			int factor )
	{
		System.out.println(" before " + 
				pav.ptList.size() + " " + 
				pav.values.size() );
		for( int i = 0; i < pav.ptList.size(); i++ )
		{
			if( i % factor != 0 )
			{
				pav.ptList.remove( i );
				pav.values.remove( i );
			}
		}
		System.out.println(" after " + 
				pav.ptList.size() + " " + 
				pav.values.size() );
	}
			

}
