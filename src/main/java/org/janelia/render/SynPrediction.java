package org.janelia.render;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.RealTransform;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.AbstractRealType;
import net.imglib2.type.numeric.real.DoubleType;


public class SynPrediction extends AbstractRealType<SynPrediction> implements Serializable, RealLocalizable
{

	private static final long serialVersionUID = -4733626426701554835L;

	public String kind;
	public SynProperties props;
	public int[] location;
	public double[] locationDouble;

	public SynPrediction(){}

	public SynPrediction( final String kind, final int[] location, final double conf )
	{
		this.kind = kind;
		this.location = location;
		props = new SynProperties( conf );
	}
	
	public SynPrediction( final String kind, final double[] location, final double conf )
	{
		this.kind = kind;
		this.locationDouble = location;
		props = new SynProperties( conf );
	}
	
	public SynPrediction( final String kind, final RealPoint location, final double conf )
	{
		this.kind = kind;
		props = new SynProperties( conf );

		locationDouble = new double[ location.numDimensions() ];
		location.localize( locationDouble );		
	}
	
	public static void main( String[] args ) throws IOException
	{

//		String path = "/data-ssd/john/flyem/small_tbars2.json";
//		String outpath = "/data-ssd/john/flyem/small_tbars2.ser";
		
//		String path = "/data-ssd/john/flyem/test_tbars2.json";
//		String outpath = "/data-ssd/john/flyem/test_tbars2.ser";
		
//		String path = "/data-ssd/john/flyem/small_synapses.json";
//		String outpath = "/data-ssd/john/flyem/small_synapses.ser";

//		String path = "/groups/saalfeld/home/bogovicj/tmp/cx_24_tmp.json";
//		String path = "/data-ssd/john/flyem/sec24/cx24_tbars_th0.8.json";
		String path = "/data-ssd/john/flyem/sec24/cx24_nl_sub.json";

//		String outpath = "/data-ssd/john/flyem/synapses.ser";
		String outpath = "/data-ssd/john/flyem/sec24/cx24_nl_sub2.json";

		System.out.println( "reading" );
		SynCollection<DoubleType> syns = SynPrediction.loadAll( path, new DoubleType() );
		System.out.println( syns.toString() );

//		System.out.println( "writing" );
//		PreSynPrediction.write( tbars, outpath );
//		TbarCollection tbars2 = PreSynPrediction.loadSerialized( outpath );
		
		System.out.println( " "  );
		System.out.println( syns.getList() );

		System.out.println( " " );
		System.out.println( " " );
		SynPrediction.write( syns, outpath );
		
		System.out.println( "done" );
	}

	public static void writeSer( SynCollection tbars, String outpath )
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
	
	public static void write( SynCollection tbars, String outpath ) throws IOException
	{
		JSONArray out = tbars.toJson();
//		System.out.println( out );
	
		Files.write( Paths.get( outpath ), out.toString().getBytes());
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

	public static <T extends RealType<T>> SynCollection<T> loadSerialized( String path, T t )
	{
		if( !path.endsWith( ".ser" ))
		{
			System.err.println("loadSerialized: path must end in .ser");
			return null;
		}
		SynCollection<T> tbars = null;
		try {
	         FileInputStream fileIn = new FileInputStream( path );
	         ObjectInputStream in = new ObjectInputStream(fileIn);
	         tbars = (SynCollection<T>) in.readObject();
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
	
	public static <T extends RealType<T>> SynCollection<T> loadAll( String path, T t )
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
		SynCollection<T> syns = SynPrediction.loadAll( obj, t );
		return syns;
	}

	public static <T extends RealType<T>> SynCollection<T> loadAll( JSONArray tbarArray, T t  )
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
		return new SynCollection<T>( tbars, min, max, t );
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

		public final double conf;

		public SynProperties( final double confidence )
		{
			this.conf = confidence;
		}
		public static SynProperties load( JSONObject obj )
		{
			double confidence = obj.getDouble( "conf" );
			return new SynProperties( confidence );
		}
	}

	public static class SynCollection<T extends RealType<T>> implements Serializable
	{
		private static final long serialVersionUID = -1063017232502453517L;

		public final ArrayList<SynPrediction> list;
		public transient final long[] min;
		public transient final long[] max;
		private transient final T t;

		public SynCollection( 
				final ArrayList<SynPrediction> list,
				final long[] min,
				final long[] max,
				final T t ){
			this.list = list;
			this.min = min;
			this.max = max;
			this.t = t;
		}
		
		public SynCollection(
				final List<RealPoint> pt,
				final List<DoubleType> vals,
				T t )
		{
			assert pt.size() == vals.size();

			ArrayList<SynPrediction> splist = new ArrayList<SynPrediction>();
			long[] min = new long[ 3 ];
			long[] max = new long[ 3 ];
			
			Arrays.fill( min, Long.MAX_VALUE );
			Arrays.fill( max, Long.MIN_VALUE );
			
			for( int i = 0; i < pt.size(); i++ )
			{
				double conf = vals.get( i ).getRealDouble();
				
				double[] pos = new double[ 3 ];
				pt.get( i ).localize( pos );
				for( int j = 0; j < 3; j++ )
				{
					long vf = (long) Math.floor( pos[ j ] );
					long vc = (long) Math.ceil( pos[ j ] );

					if( vf < min[ j ] )
						min[ j ] = vf;

					if( vc > max[ j ] )
						max[ j ] = vc;
				}

				SynPrediction sp = new SynPrediction( "PreSyn", pos, conf );
				splist.add( sp );
			}

			this.list = splist;
			this.min = min;
			this.max = max;
			this.t = t;
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
		
		public List<T> getValues( final double thresh )
		{
			return list.stream()
					.map( x -> x.props.conf > thresh ? one() : zero() )
					.collect( Collectors.toList() );
		}
		
		public List<T> getValues()
		{
			return list.stream()
					.map( x -> set( x.props.conf ))
					.collect( Collectors.toList() );
		}

		private T set( double v )
		{
			T out = t.createVariable();
			out.setReal( v );
			return out;
		}

		private T zero()
		{
			T out = t.createVariable();
			out.setZero();
			return out;
		}

		private T one()
		{
			T out = t.createVariable();
			out.setOne();
			return out;
		}
		
		public List<RealPoint> getPoints()
		{
			return list.stream()
					.map( x -> new RealPoint( (double)x.location[0], (double)x.location[1], (double)x.location[2] ))
					.collect( Collectors.toList() ); 
		}
		
		public List<RealPoint> getPointsDouble()
		{
			return list.stream()
					.map( x -> new RealPoint( (double)x.locationDouble[0], (double)x.locationDouble[1], (double)x.locationDouble[2] ))
					.collect( Collectors.toList() ); 
		}

		public ArrayList<SynPrediction> getList()
		{
			return list;
		}

		public JSONArray toJson()
		{
			JSONArray out = new JSONArray();
			List< JSONObject > y = list.stream().map( SynPrediction::toJson ).collect( Collectors.toList() );
			out.put( y );
			return out;
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
		return props.conf > 0.85 ? 50 : 0;
	}

	@Override
	public float getRealFloat()
	{
		return props.conf > 0.85 ? 50f : 0f;
	}

	@Override
	public void setReal( float f )
	{
		// not set-able, do nothing
	}

	@Override
	public void setReal( double f )
	{
		// not set-able, do nothing
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
		return props.conf == t.props.conf;
	}
	
	public JSONObject toJson()
	{
		JSONObject p = new JSONObject();
		p.put( "conf", this.props.conf );

		JSONObject obj = new JSONObject();
		obj.put( "Kind", this.kind );
		
		if( this.location != null )
			obj.put( "Pos", this.location );
		else if ( this.locationDouble != null )
			obj.put( "Pos", this.locationDouble );

		obj.put( "Prop", p );

		return obj;
	}
}
