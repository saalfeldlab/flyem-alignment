package net.imglib2;

import net.imglib2.view.Views;

public class RealImgAndInterval<T>
{
	public final Interval itvl;
	public final RealRandomAccessible<T> rra;
	
	public RealImgAndInterval(
		final Interval itvl,
		final RealRandomAccessible<T> rra )
	{
		this.rra = rra;
		this.itvl = itvl;
	}
	
	public RandomAccessibleInterval<T> get()
	{
		return Views.interval( Views.raster( rra ), itvl );
	}
}
