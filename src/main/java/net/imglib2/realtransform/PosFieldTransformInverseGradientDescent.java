package net.imglib2.realtransform;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;
import org.ejml.ops.NormOps;

import jitk.spline.TransformInverseGradientDescent;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPositionable;

import java.util.Arrays;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class PosFieldTransformInverseGradientDescent implements RealTransform
{
	int ndims;

	DenseMatrix64F jacobian;

	DenseMatrix64F directionalDeriv; // derivative in direction of dir (the
										// descent direction )

	DenseMatrix64F descentDirectionMag; // computes dir^T directionalDeriv
										// (where dir^T is often
										// -directionalDeriv)

	DenseMatrix64F dir; // descent direction

	DenseMatrix64F errorV; // error vector ( errorV = target - estimateXfm )

	DenseMatrix64F estimate; // current estimate

	DenseMatrix64F estimateXfm; // current estimateXfm

	DenseMatrix64F target;

	boolean fixZ = false;

	double error = 9999.0;

	double stepSz = 1.0;

	double beta = 0.7;
	
	double tolerance = 0.5;
	
	double c = 0.0001;
	
	int maxIters = 100;
	
	double jacobianEstimateStep = 1.0;
	double jacobianRegularizationEps = 0.1;
	DenseMatrix64F jacRegMatrix;
	
	int stepSizeMaxTries = 8;
	
	double maxStepSize = Double.MAX_VALUE;

	double minStepSize = 1e-9;

	private RealTransform xfm;
	
	private double[] guess; // initialization for iterative inverse
	private double[] src;
	private double[] tgt;
	
	protected static Logger logger = LogManager.getLogger(
			PosFieldTransformInverseGradientDescent.class.getName() );

	public PosFieldTransformInverseGradientDescent( int ndims, RealTransform xfm )
	{
		this.ndims = ndims;
		this.xfm = xfm;
		dir = new DenseMatrix64F( ndims, 1 );
		errorV = new DenseMatrix64F( ndims, 1 );
		directionalDeriv = new DenseMatrix64F( ndims, 1 );
		descentDirectionMag = new DenseMatrix64F( 1, 1 );
		jacobian = new DenseMatrix64F( ndims, ndims );
		
		jacRegMatrix = CommonOps.identity( ndims, ndims );
		CommonOps.scale( jacobianRegularizationEps, jacRegMatrix );
		
		
		src = new double[ ndims ];
		tgt = new double[ ndims ];
	}

	public void setBeta( double beta )
	{
		this.beta = beta;
	}

	public void setC( double c )
	{
		this.c = c;
	}

	public void setTolerance( final double tol )
	{
		this.tolerance = tol;
	}
	
	public void setMaxIters( final int maxIters )
	{
		this.maxIters = maxIters;
	}

	public void setFixZ( boolean fixZ )
	{
		this.fixZ = fixZ;
	}

	public void setStepSize( double stepSize )
	{
		stepSz = stepSize;
	}

	public void setMinStep( double minStep )
	{
		this.minStepSize = minStep;
	}
	
	public void setMaxStep( double maxStep )
	{
		this.maxStepSize = maxStep;
	}
	
	public void setJacobianEstimateStep( final double jacStep )
	{
		this.jacobianEstimateStep = jacStep;
	}
	
	public void setJacobianRegularizationEps( final double e )
	{
		this.jacobianRegularizationEps = e;
		jacRegMatrix = CommonOps.identity( ndims, ndims );
		CommonOps.scale( jacobianRegularizationEps, jacRegMatrix );
	}

	public void setStepSizeMaxTries( int stepSizeMaxTries )
	{
		this.stepSizeMaxTries = stepSizeMaxTries;
	}
	
	public void setTarget( double[] tgt )
	{
		// System.out.println("set target");
		if( this.target == null )
			this.target = new DenseMatrix64F( ndims, 1 );

		for( int i = 0; i < ndims; i++ )
			target.set( i, tgt[ i ] );
	}

	public DenseMatrix64F getErrorVector()
	{
		return errorV;
	}

	public DenseMatrix64F getDirection()
	{
		return dir;
	}

	public void setEstimate( double[] est )
	{
		this.estimate = new DenseMatrix64F( ndims, 1 );
		estimate.setData( est );
	}

	public void setEstimateXfm( double[] est )
	{
		this.estimateXfm = new DenseMatrix64F( ndims, 1 );
		estimateXfm.setData( est );
		updateError();
	}

	public DenseMatrix64F getEstimate()
	{
		return estimate;
	}

	public double getError()
	{
		return error;
	}

	public int numSourceDimensions()
	{
		return ndims;
	}

	@Override
	public int numTargetDimensions()
	{
		return ndims;
	}

	@Override
	public RealTransform copy()
	{
		return new  PosFieldTransformInverseGradientDescent( ndims, xfm );
	}
	
	public void setGuess( final double[] guess )
	{
		this.guess = guess;
	}
	
	public void apply( final double[] s, final double[] t )
	{
		
		// needs to be able to work in place,
		// so dont work with s and t directly
        //
        // copy s into src
		System.arraycopy( s, 0, src, 0, s.length );

		
		// initial guess is source
		if( guess != null )
			System.arraycopy( guess, 0, tgt, 0, guess.length );
		else
			System.arraycopy( src, 0, tgt, 0, src.length );

		// tgt is the error estimate
		double err = inverseTol( src, tgt, tolerance, maxIters );
		
        // copy tgt into t
		System.arraycopy( tgt, 0, t, 0, t.length );
		
//		if( err > tolerance )
//			System.out.println( "err: " + err + " >  EPS ( " + tolerance + " )" );
	}

	public void apply( final float[] src, final float[] tgt )
	{
		double[] srcd = new double[ src.length ];
		double[] tgtd = new double[ tgt.length ];
		for( int i = 0; i < src.length; i++ )
			srcd[ i ] = src[ i ];

		apply( srcd, tgtd );
		
		for( int i = 0; i < tgt.length; i++ )
			tgt[ i ] = (float)tgtd[ i ];
	}
	
	public void apply( final RealLocalizable src, final RealPositionable tgt )
	{
		double[] srcd = new double[ src.numDimensions() ];
		double[] tgtd = new double[ tgt.numDimensions() ];
		src.localize( srcd );
		apply( src, tgt );
		tgt.setPosition( tgtd );
	}
	
	public double inverseTol( final double[] target, final double[] guess, final double tolerance, final int maxIters )
	{
		// TODO - have a flag in the apply method to also return the derivative
		// if requested
		// to prevent duplicated effort
		
//		double error = 999 * tolerance;

		/* 
		 * initialize the error to a big enough number 
		 * This shouldn't matter since the error is updated below
		 * after the estimate updated.
		 */
		error = 999 * tolerance; 
		
		final double[] guessXfm = new double[ ndims ];

//		System.out.println( " " );
//		System.out.println( "trgt : " + Arrays.toString( target ));
//		System.out.println( "gues : " + Arrays.toString( guess ));

		xfm.apply( guess, guessXfm );

		setTarget( target );
		setEstimate( guess );
		setEstimateXfm( guessXfm ); // this calls update error

//		System.out.println("pre-error    : " + error );
		double t0 = getError();
		double t = 1.0;

		int k = 0;
		while ( error >= tolerance && k < maxIters )
		{
//			computeDirection( guess, guessXfm );

			if( fixZ )
				estimateJacobian2d( jacobianEstimateStep );
			else
				estimateJacobian( jacobianEstimateStep );

			if( jacobianRegularizationEps > 0 )
				regularizeJacobian();
			
			computeDirection();


			/* the two below lines should give identical results */
//			t = backtrackingLineSearch( c, beta, stepSizeMaxTries, t0 );
			t = backtrackingLineSearch( t0 );

			if ( t == 0.0 )
				break;

//			System.out.println(" step size: " + t );
//			System.out.println( "jac : " + jacobian);
//			System.out.println(" direction : " + dir );

			updateEstimate( t );  // go in negative direction to reduce cost
			updateError();

			TransformInverseGradientDescent.copyVectorIntoArray( getEstimate(), guess );
			xfm.apply( guess, guessXfm );

			t0 = getError();

			setEstimateXfm( guessXfm );
			error = getError();

//			System.out.println("error    : " + error );
//			System.out.println("estimate : " +  estimate );
//			System.out.println("##########################");
//			System.out.println(" ");
			
			k++;
		}

//		if( error > tolerance )
//		{
//			System.out.println( "WARNING : error " + error + 
//					" for point " + Arrays.toString( this.target.data ) + " " + 
//					", setting estimate to NaN" );
//			
//			for( int i=0; i<ndims; i++ )
//				estimate.set( i, Double.NaN );
//		}
		return error;
	}

//	public void computeDirection( double[] srcpt, double[] tgtpt )
	public void computeDirection()
	{
		
		

//		for( int i = 0; i < srcpt.length; i++ )
//			errorV.set( i, tgtpt[i] - srcpt[i] );

		CommonOps.solve( jacobian, errorV, dir );

		double norm = NormOps.normP2( dir );
		CommonOps.divide( norm, dir );

		// compute the directional derivative
		CommonOps.mult( jacobian, dir, directionalDeriv );
		CommonOps.multTransA( dir, directionalDeriv, descentDirectionMag );
		
		
//		System.out.println( "error    : " + errorV );
//		System.out.println( "dir      : " + dir );
//		System.out.println( "jacobian : " + jacobian );
//		System.out.println( "ddir     : " + descentDirectionMag );
	}
	
	public void estimateJacobian( double step )
	{
		double[] srcpt = estimate.data;

		double[] p = new double[ ndims ];
		double[] q = new double[ ndims ];
		double[] qc = new double[ ndims ];
		
//		System.out.println( " " );
//		System.out.println( "########################################" );
		
		xfm.apply( srcpt, qc );

//		System.out.println( "sc : " + Arrays.toString( srcpt ));
//		System.out.println( "qc : " + Arrays.toString( qc ));
//		System.out.println( " " );

		for( int i = 0; i < ndims; i++ )
		{
			for( int j = 0; j < ndims; j++ )
				if( j == i )
					p [ j ] = srcpt[ j ] + step;
				else
					p [ j ] = srcpt[ j ];

			xfm.apply( p, q );
			
//			System.out.println( "p : " + Arrays.toString(p));
//			System.out.println( "q : " + Arrays.toString(q));
//			System.out.println( " " );
			
			for( int j = 0; j < ndims; j++ )
			{
//				jacobian.set( i, j, ( q[j] - qc[j] ) / step );
				jacobian.set( j, i, ( q[j] - qc[j] ) / step );
			}
		}
//		System.out.println( "########################################" );
	}
	
	public void estimateJacobian2d( double step )
	{
		double[] srcpt = estimate.data;

		double[] p = new double[ ndims ];
		double[] q = new double[ ndims ];
		double[] qc = new double[ ndims ];
		
//		System.out.println( " " );
//		System.out.println( "########################################" );
		
		xfm.apply( srcpt, qc );

//		System.out.println( "FZ sc : " + Arrays.toString( srcpt ));
//		System.out.println( "FZ qc : " + Arrays.toString( qc ));
//		System.out.println( " " );

//		int nd = 2;
		for( int i = 0; i < ndims; i++ )
		{
			for( int j = 0; j < ndims; j++ )
				if( j == i )
					p [ j ] = srcpt[ j ] + step;
				else
					p [ j ] = srcpt[ j ];

			xfm.apply( p, q );
			
//			System.out.println( "FZ p : " + Arrays.toString(p));
//			System.out.println( "FZ q : " + Arrays.toString(q));
//			System.out.println( " " );
			
			for( int j = 0; j < 2; j++ )
			{
//				jacobian.set( i, j, ( q[j] - qc[j] ) / step );
				jacobian.set( j, i, ( q[j] - qc[j] ) / step );
			}
			jacobian.set( 2, 2, 1.0 );
		}
//		System.out.println( "########################################" );
	}
	
	public void regularizeJacobian()
	{
		// Changes jacobian (J) to be:
		// 	   ( 1-eps ) * J + ( eps ) * I 
		// 
		// note jacRegMatrix = eps * I 
		CommonOps.scale( ( 1 - jacobianRegularizationEps ), jacobian );
		CommonOps.add( jacRegMatrix, jacobian, jacobian );
	}

	/**
	 * Uses Backtracking Line search to determine a step size.
	 * 
	 * @param c the armijoCondition parameter
	 * @param beta the fraction to multiply the step size at each iteration ( less than 1 )
	 * @param maxtries max number of tries
	 * @param t0 initial step size
	 * @return the step size
	 */
	public double backtrackingLineSearch( double t0 )
	{
//		System.out.println("t0: " + t0 );
//		System.out.println("beta: " + beta );
//		System.out.println("c: " + c );
		double t = t0; // step size

		int k = 0;
		// boolean success = false;
		while ( k < stepSizeMaxTries )
		{
			if ( armijoCondition( c, t ) )
			{
				// success = true;
				break;
			}
			else
				t *= beta;

			k++;
		}
		
		if( t < minStepSize )
			return minStepSize;
		
		if( t > maxStepSize )
			return maxStepSize;

//		logger.trace( "selected step size after " + k + " tries" );

		return t;
	}
	
	/**
	 * Uses Backtracking Line search to determine a step size.
	 * 
	 * @param c the armijoCondition parameter
	 * @param beta the fraction to multiply the step size at each iteration ( less than 1 )
	 * @param maxtries max number of tries
	 * @param t0 initial step size
	 * @return the step size
	 */
	public double backtrackingLineSearch( double c, double beta, int maxtries, double t0 )
	{
//		System.out.println("t0: " + t0 );
//		System.out.println("beta: " + beta );
//		System.out.println("c: " + c );
		double t = t0; // step size

		int k = 0;
		// boolean success = false;
		while ( k < maxtries )
		{
			if ( armijoCondition( c, t ) )
			{
				// success = true;
				break;
			}
			else
				t *= beta;

			k++;
		}

//		logger.trace( "selected step size after " + k + " tries" );

		return t;
	}

	/**
	 * Returns true if the armijo condition is satisfied.
	 * 
	 * @param c the c parameter
	 * @param t the step size
	 * @return true if the step size satisfies the condition
	 */
	public boolean armijoCondition( double c, double t )
	{
		double[] d = dir.data;
		double[] x = estimate.data; // give a convenient name

		double[] x_ap = new double[ ndims ];
		for ( int i = 0; i < ndims; i++ )
			x_ap[ i ] = x[ i ] + t * d[ i ];

		// don't have to do this in here - this should be reused
		// double[] phix = xfm.apply( x );
		// TODO make sure estimateXfm is updated at the correct time
		double[] phix = estimateXfm.data;
		double[] phix_ap = new double[ this.ndims ];
		xfm.apply( x_ap, phix_ap );

		double fx = squaredError( phix );
		double fx_ap = squaredError( phix_ap );

		// descentDirectionMag is a scalar
		// computeExpectedDescentReduction();
//		CommonOps.multTransA( dir, directionalDeriv, descentDirectionMag );
//		logger.debug( "descentDirectionMag: " + descentDirectionMag.get( 0 ) );

		double m = sumSquaredErrorsDeriv( this.target.data, phix ) * descentDirectionMag.get( 0 );
//		m = Math.sqrt( m );
		
//		System.out.println( "   x          : " + Arrays.toString( phix ));
//		System.out.println( "   x + ap     : " + Arrays.toString( phix_ap ));
//		System.out.println( "   f( x )     : " + fx );
//		System.out.println( "   f( x + ap ): " + fx_ap );
//		System.out.println( "   p^T d      : " + descentDirectionMag.get( 0 ));
//		System.out.println( "   m          : " + m );
//		System.out.println( "   c * m * t  : " + c * t * m );
//		System.out.println( "   f( x ) + c * m * t: " + ( fx + c * t * m ) );
		
//		System.out.println( "step            : " + t );
//		System.out.println( "fx_ap           : " + fx_ap );
//		System.out.println( "fx + c * t * m  : " + ( fx + c * t * m ) );
		
//		logger.trace( "   f( x )     : " + fx );
//		logger.trace( "   f( x + ap ): " + fx_ap );
//		logger.debug( "   p^T d      : " + descentDirectionMag.get( 0 ));
//		logger.debug( "   m          : " + m );
//		logger.debug( "   c * m * t  : " + c * t * m );
//		logger.trace( "   f( x ) + c * m * t: " + ( fx + c * t * m ) );

		if ( fx_ap < fx + c * t * m )
			return true;
		else
			return false;
	}

	public double squaredError( double[] x )
	{
		double error = 0;
		for ( int i = 0; i < ndims; i++ )
			error += ( x[ i ] - this.target.get( i ) ) * ( x[ i ] - this.target.get( i ) );

		return error;
	}

	public void updateEstimate( double stepSize )
	{
//		logger.trace( "step size: " + stepSize );
//		logger.trace( "estimate:\n" + estimate );

		// go in the negative gradient direction to minimize cost
//		CommonOps.scale( -stepSize / norm, dir );
//		CommonOps.addEquals( estimate, dir );
		
		// dir should be pointing in the descent direction
		CommonOps.addEquals( estimate, stepSize, dir );

//		logger.trace( "new estimate:\n" + estimate );
	}
	
	public void updateEstimateNormBased( double stepSize )
	{
//		logger.debug( "step size: " + stepSize );
//		logger.trace( "estimate:\n" + estimate );

		double norm = NormOps.normP2( dir );
//		logger.debug( "norm: " + norm );

		// go in the negative gradient direction to minimize cost
		if ( norm > stepSize )
		{
			CommonOps.scale( -stepSize / norm, dir );
		}
		
		CommonOps.addEquals( estimate, dir );
		
//		logger.trace( "new estimate:\n" + estimate );
	}

	public void updateError()
	{
		if ( estimate == null || target == null )
		{
			System.err.println( "WARNING: Call to updateError with null target or estimate" );
			return;
		}

		// errorV = estimate - target
//		CommonOps.sub( estimateXfm, target, errorV );
		
		// ( errorV = target - estimateXfm  )
		CommonOps.sub( target, estimateXfm, errorV );
		
//		System.out.println( "#########################" );
//		System.out.println( "updateError, estimate   :\n" + estimate );
//		System.out.println( "updateError, estimateXfm:\n" + estimateXfm );
//		System.out.println( "updateError, target     :\n" + target );
//		System.out.println( "updateError, error      :\n" + errorV );
//		System.out.println( "#########################" );
		
		// set scalar error equal to max of component-wise errors
		error = Math.abs( errorV.get( 0 ) );
		for ( int i = 1; i < ndims; i++ )
		{
			if ( Math.abs( errorV.get( i ) ) > error )
				error = Math.abs( errorV.get( i ) );
		}

	}

	/**
	 * This function returns \nabla f ^T \nabla f where f = || y - x ||^2 and
	 * the gradient is taken with respect to x
	 * 
	 * @param y
	 * @param x
	 * @return
	 */
	private double sumSquaredErrorsDeriv( double[] y, double[] x )
	{
		double errDeriv = 0.0;
		for ( int i = 0; i < ndims; i++ )
			errDeriv += ( y[ i ] - x[ i ] ) * ( y[ i ] - x[ i ] );

		return 2 * errDeriv;
	}

	public static double sumSquaredErrors( double[] y, double[] x )
	{
		int ndims = y.length;

		double err = 0.0;
		for ( int i = 0; i < ndims; i++ )
			err += ( y[ i ] - x[ i ] ) * ( y[ i ] - x[ i ] );

		return err;
	}

	public static void copyVectorIntoArray( DenseMatrix64F vec, double[] array )
	{
		System.arraycopy( vec.data, 0, array, 0, vec.getNumElements() );
	}

}
