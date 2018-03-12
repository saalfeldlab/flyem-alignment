package net.imglib2.realtransform;

import java.util.ArrayList;
import java.util.Arrays;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;

public class IncrementalInverter implements RealTransform
{

	private RealTransformSequence totalXfm;
	
	private RealTransform fwdXfm;
	private PosFieldTransformInverseGradientDescent invXfm;

	private final double initialTolerance;
	private double tolerance = 0.34;
	private double toleranceIncreaseFactor = 2;
	private int maxTries = 15;
	
	private int inverterIndex;
	private int defaultIndex;

	/* Transforms before and after the inverse */
	private ArrayList< RealTransform > before;
	private ArrayList< RealTransform > after;
	
	private ArrayList< double[] > estimates;
	private ArrayList< Double > errors;
	
	private double[] bestEstimate;
	private double bestError;
	
	static final double[] C_LIST = new double[]{
			0.000001, 
			0.005 };
	
	static final double[] BETA_LIST = new double[]{ 
			0.5,
			0.3 };
	
	static final int[] ITER_LIST = new int[]{ 
			2000,
			2000 };
	
	static final int[] STEP_ITER_LIST = new int[]{ 
			12,
			6 };
	
	static final int[] MAX_STEP_LIST = new int[]{ 
			999,
			999 };
	
	static final double[] MIN_STEP_LIST = new double[]{ 
			0.2,
			0.2 };
	
	
	public IncrementalInverter(
			ArrayList<RealTransform> before,
			RealTransform fwd,
			ArrayList<RealTransform> after,
			double initialTolerance )
	{
		this.initialTolerance = initialTolerance;
		tolerance = initialTolerance;
		inverterIndex = 0;

		this.before = before;
		this.fwdXfm = fwd;
		this.after = after;

		estimates = new ArrayList< double[] >();
		errors = new ArrayList< Double >();
	}
	
	public void setDefaultIndex( int idx )
	{
		this.defaultIndex = idx;
	}
	
	public void setToleranceIncreaseFactor( double factor)
	{
		this.toleranceIncreaseFactor = factor;
	}
	
	boolean fixZ = false;
	public void setFixZ( boolean fixZ )
	{
		this.fixZ = fixZ;
	}
	
	public PosFieldTransformInverseGradientDescent getNextInverter()
	{
		invXfm = new PosFieldTransformInverseGradientDescent( 3, fwdXfm );
		
		if( inverterIndex < C_LIST.length )
		{
			invXfm.setTolerance( tolerance );
			invXfm.setMaxIters( ITER_LIST[ inverterIndex ] );
			invXfm.setStepSizeMaxTries( STEP_ITER_LIST[ inverterIndex ] );
			invXfm.setBeta( BETA_LIST[ inverterIndex ] );
			invXfm.setC( C_LIST[ inverterIndex ] );
			invXfm.setMaxStep( MAX_STEP_LIST[ inverterIndex ] );
			invXfm.setMinStep( MIN_STEP_LIST[ inverterIndex ] );
			
		}
		else
		{
			tolerance *= toleranceIncreaseFactor;

			invXfm.setTolerance( tolerance );
			invXfm.setMaxIters( ITER_LIST[ defaultIndex ] );
			invXfm.setStepSizeMaxTries( STEP_ITER_LIST[ defaultIndex ] );
			invXfm.setBeta( BETA_LIST[ defaultIndex ] );
			invXfm.setC( C_LIST[ defaultIndex ] );
			invXfm.setMaxStep( MAX_STEP_LIST[ defaultIndex ] );
			invXfm.setMinStep( MIN_STEP_LIST[ defaultIndex ] );
		}
		invXfm.setFixZ( fixZ );
		return invXfm;
	}

	public void apply( double[] src, double[] target  )
	{
		double[] guess = new double[ src.length ];
		System.arraycopy( src, 0, guess, 0, src.length );
		run( src, target, guess );
	}

	public void run( double[] src, double[] target, double[] guess )
	{

		inverterIndex = 0;
		tolerance = initialTolerance;
		estimates.clear();
		errors.clear();
		
		bestError = Double.MAX_VALUE;
		bestEstimate = null;

		int i = 0;
		while( i < maxTries  )
		{
			/* Build the transformation */
			totalXfm = new RealTransformSequence();
			if( before != null ) {
				for( RealTransform bx : before )
				{
//					System.out.println( "before adding " + bx );
					totalXfm.add( bx );
				}
			}
			
			if( bestEstimate != null )
				System.arraycopy( bestEstimate, 0, guess, 0, bestEstimate.length );

			invXfm = getNextInverter();
			invXfm.setGuess( guess );

			totalXfm.add( invXfm );
			
//			System.out.println( invXfm );
			
			if( after != null ) {
				for( RealTransform ax : after )
				{
//					System.out.println( "after adding " + ax );
					totalXfm.add( ax );
				}
			}

			/* Apply it */
			totalXfm.apply( src, target );
			
//			if( Double.isNaN(target[0]))
//			{
//				System.out.println("noes");
//			}
			
//			System.out.println( "src : " + Arrays.toString( src ));
//			System.out.println( "guess : " + Arrays.toString( guess ));
//			System.out.println( "target : " + Arrays.toString( target ));

			
			/* Add to list */
			double[] thisEstimate = new double[ target.length ];
			System.arraycopy( target, 0, thisEstimate, 0, target.length );

			double err = getLastError();
			errors.add( err );
			estimates.add( thisEstimate );

//			System.out.println("tol : " + tolerance );
//			System.out.println( "err: " + err );
//			System.out.println( " " );

			if( err < bestError )
			{
				bestError = err;
				bestEstimate = thisEstimate;
			}
			
			if( err < tolerance )
			{
//				System.out.println("err better than tolerance " );
				break;
			}
			i++;
			inverterIndex++;
		}
		
		if( bestEstimate == null || target == null ||
				Double.isNaN( target[0] )  )
		{
			System.err.println("Warning: transform at or near point " +
					Arrays.toString( src ) +
					" may be singular, can't invert." );

			bestEstimate = new double[ 3 ];
			bestError = 987654321;
		}
		
		System.arraycopy( bestEstimate, 0, target, 0, target.length );
	}

	public double getLastError()
	{
		return invXfm.getError();
	}
	
	public double getBestError()
	{
		return bestError;
	}

	
	/**
	 * Source and target dimensions are flipped relative to fwd
	 */
	@Override
	public int numSourceDimensions()
	{
		return fwdXfm.numTargetDimensions();
	}

	/**
	 * Source and target dimensions are flipped relative to fwd
	 */
	@Override
	public int numTargetDimensions()
	{
		return fwdXfm.numSourceDimensions();
	}

	@Override
	public void apply( RealLocalizable source, RealPositionable target )
	{
		double[] s = new double[ source.numDimensions() ];
		double[] t = new double[ target.numDimensions() ];
		source.localize( s );
		apply( s, t );
		target.setPosition( t );
	}

	@Override
	public RealTransform copy()
	{
		return null;
	}
}
