package net.rcode.mapnikdistiller;

/**
 * Conditions that control whether a layer renders
 * @author stella
 *
 */
public class RenderConditions {
	/**
	 * Minimum scale denominator we are rendering at or NaN
	 */
	private double minScaleDenominator=0;
	
	/**
	 * Maximum scale denominator we are rendering at or NaN
	 */
	private double maxScaleDenominator=Double.POSITIVE_INFINITY;

	public void setMinScaleDenominator(double minScaleDenominator) {
		this.minScaleDenominator = minScaleDenominator;
	}

	public double getMinScaleDenominator() {
		return minScaleDenominator;
	}

	public void setMaxScaleDenominator(double maxScaleDenominator) {
		this.maxScaleDenominator = maxScaleDenominator;
	}

	public double getMaxScaleDenominator() {
		return maxScaleDenominator;
	}
}
