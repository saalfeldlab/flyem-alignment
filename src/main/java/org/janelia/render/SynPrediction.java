package org.janelia.render;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.real.AbstractRealType;
import net.imglib2.type.numeric.real.DoubleType;


public class SynPrediction extends AbstractRealType<SynPrediction> implements Serializable, RealLocalizable
{

	private static final long serialVersionUID = -4733626426701554835L;

	public String kind;
	public SynProperties props;
	public int[] location;

	public static void main( String[] args ) throws IOException
	{

//		String path = "/data-ssd/john/flyem/small_tbars2.json";
//		String outpath = "/data-ssd/john/flyem/small_tbars2.ser";
		
//		String path = "/data-ssd/john/flyem/test_tbars2.json";
//		String outpath = "/data-ssd/john/flyem/test_tbars2.ser";
		
//		String path = "/data-ssd/john/flyem/small_synapses.json";
//		String outpath = "/data-ssd/john/flyem/small_synapses.ser";

//		String path = "/groups/saalfeld/home/bogovicj/tmp/cx_24_tmp.json";
		String path = "/data-ssd/john/flyem/sec24/cx24_tbars_th0.8.json";

		String outpath = "/data-ssd/john/flyem/synapses.ser";

		System.out.println( "reading" );
		SynCollection syns = SynPrediction.loadAll( path );
		System.out.println( syns.toString() );
		
//		System.out.println( "writing" );
//		PreSynPrediction.write( tbars, outpath );
//		TbarCollection tbars2 = PreSynPrediction.loadSerialized( outpath );
		
		System.out.println( "done" );
	}

	public static void write( SynCollection tbars, String outpath )
	{
		try {
	         FileOutputStream fileOut = new FileOutputStream( outpath );
	         ObjectOutputStream out = new ObjectOutputStream(fileOut);

	         out.writeObject( tbars );
	         out.close();
	         fileOut.close();
	         System.out.printf("Serialized data is saved in " + outpath + "\n" );
		}
		catch(IOException i)
		{
			i.printStackTrace();
		}
	}
	
	public static SynPrediction load( JSONObject obj )
	{
		SynPrediction out = new SynPrediction();
		out.kind = obj.getString( "Kind" );

		JSONObject propsObj = (JSONObject)obj.get( "Prop" );
		out.props = SynProperties.load( propsObj );

		JSONArray locArray = (JSONArray)obj.get( "Pos" );
		out.location = new int[ 3 ];
		out.location[ 0 ] = locArray.getInt( 0 );
		out.location[ 1 ] = locArray.getInt( 1 );
		out.location[ 2 ] = locArray.getInt( 2 );

		return out;
	}

	public static SynCollection loadSerialized( String path )
	{
		if( !path.endsWith( ".ser" ))
		{
			System.err.println("loadSerialized: path must end in .ser");
			return null;
		}
		SynCollection tbars = null;
		try {
	         FileInputStream fileIn = new FileInputStream( path );
	         ObjectInputStream in = new ObjectInputStream(fileIn);
	         tbars = (SynCollection) in.readObject();
	         in.close();
	         fileIn.close();
	      }catch(IOException i) {
	         i.printStackTrace();
	         return null;
	      }catch(ClassNotFoundException c) {
	         System.out.println("Employee class not found");
	         c.printStackTrace();
	         return null;
	      }
		return tbars;
	}
	
	public static SynCollection loadAll( String path )
	{
//		String jsonData = "";
		BufferedReader br = null;
		StringBuffer jsonData = new StringBuffer();
		try {
			String line;
			br = new BufferedReader(new FileReader( path ));
			System.out.println("reading");
			int i = 0;
			while ((line = br.readLine()) != null) {
				jsonData.append( line );
				jsonData.append( '\n' );
				i++;

//				if( i % 10000 == 0 )
//				{
//					System.out.println( "line i: " + i + " out of ~12m" );
//				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		JSONArray obj = new JSONArray( jsonData.toString() );
		SynCollection syns = SynPrediction.loadAll( obj );
		return syns;
	}

	public static SynCollection loadAll( JSONArray tbarArray )
	{
		ArrayList<SynPrediction> tbars = new ArrayList<SynPrediction>();
		long[] min = new long[ 3 ];
		long[] max = new long[ 3 ];

		Arrays.fill( min, Long.MAX_VALUE );
		Arrays.fill( max, Long.MIN_VALUE );

		int i = 0;
		for( Object obj : tbarArray )
		{
			SynPrediction tbp = SynPrediction.load((JSONObject) obj);
			updateMinMax( min, max, tbp );
			tbars.add( tbp );

			i++;
//			System.out.println( "i: " + i + " out of ~100k" );
//			if( i % 1000 == 0 )
//			{
//				System.out.println( "i: " + i + " out of ~100k" );
//			}
		}
		return new SynCollection( tbars, min, max );
	}

	public static void updateMinMax( final long[] min, final long[] max, final SynPrediction tbp )
	{
		for( int d = 0; d < min.length; d++ )
		{
			if( tbp.location[ d ] < min[ d ])
				min[ d ] = tbp.location[ d ];
			
			if( tbp.location[ d ] > max[ d ])
				max[ d ] = tbp.location[ d ];
		}
	}
	
	public static class SynProperties implements Serializable
	{
		private static final long serialVersionUID = 51530766918420051L;

		public final double confidence;

		public SynProperties( final double confidence )
		{
			this.confidence = confidence;
		}
		public static SynProperties load( JSONObject obj )
		{
			double confidence = obj.getDouble( "conf" );
			return new SynProperties( confidence );
		}
	}

	public static class SynCollection implements Serializable
	{
		private static final long serialVersionUID = -1063017232502453517L;

		public final ArrayList<SynPrediction> list;
		public final long[] min;
		public final long[] max;

		public SynCollection( 
				final ArrayList<SynPrediction> list,
				final long[] min,
				final long[] max ){
			this.list = list;
			this.min = min;
			this.max = max;
		}

		@Override
		public String toString()
		{
			System.out.println( "tostring" );
			String out = "Synapses ( " + list.size() + ") ";
			out += Arrays.toString( min ) + " : ";
			out += Arrays.toString( max );
			return out;
		}
		
		public List<DoubleType> getValues( final double thresh )
		{
			return list.stream()
					.map( x -> x.props.confidence > thresh ? new DoubleType(1) : new DoubleType( 0 ) )
					.collect( Collectors.toList() );
		}
		
		public List<RealPoint> getPoints()
		{
			return list.stream()
					.map( x -> new RealPoint( (double)x.location[0], (double)x.location[1], (double)x.location[2] ))
					.collect( Collectors.toList() );
		}
		
	}

	public static List<RealPoint> transformPoints( final List<RealPoint> pts, final RealTransform xfm )
	{
		return pts.stream().map( x -> transform( x, xfm ) )
			.collect( Collectors.toList() );
	}

	public static RealPoint transform( final RealLocalizable pt, final RealTransform xfm )
	{
		RealPoint out = new RealPoint( pt.numDimensions() );
		xfm.apply( pt, out );
		return out;
	}

	@Override
	public int numDimensions()
	{
		return 3;
	}

	@Override
	public void localize( float[] position )
	{
		position[ 0 ] = (float)location[ 0 ];
		position[ 1 ] = (float)location[ 1 ];
		position[ 2 ] = (float)location[ 2 ];
	}

	@Override
	public void localize( double[] position )
	{
		position[ 0 ] = (double)location[ 0 ];
		position[ 1 ] = (double)location[ 1 ];
		position[ 2 ] = (double)location[ 2 ];
	}

	@Override
	public float getFloatPosition( int d )
	{
		return (float)location[ d ];
	}

	@Override
	public double getDoublePosition( int d )
	{
		return (double)location[ d ];
	}

	@Override
	public double getMaxValue()
	{
		return 1;
	}

	@Override
	public double getMinValue()
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getMinIncrement()
	{
		// TODO Auto-generated method stub
		return 0.001;
	}

	@Override
	public int getBitsPerPixel()
	{
		return 0;
	}

	@Override
	public double getRealDouble()
	{
		return props.confidence > 0.85 ? 50 : 0;
	}

	@Override
	public float getRealFloat()
	{
		return props.confidence > 0.85 ? 50f : 0f;
	}

	@Override
	public void setReal( float f )
	{
		// TODO Auto-generated method stub
	}

	@Override
	public void setReal( double f )
	{
		// TODO Auto-generated method stub
	}

	@Override
	public SynPrediction createVariable()
	{
		return null;
	}

	@Override
	public SynPrediction copy()
	{
		return null;
	}

	@Override
	public boolean valueEquals( SynPrediction t )
	{
		return props.confidence == t.props.confidence;
	}
}
