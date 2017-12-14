package org.waltonrobotics.motion.curve;

import org.waltonrobotics.DriveTrainInterface;
import org.waltonrobotics.Robot;
import org.waltonrobotics.controller.Point;
import org.waltonrobotics.motion.Path;

/**
 * Resources:
 * https://pages.mtu.edu/~shene/COURSES/cs3621/NOTES/spline/Bezier/bezier-der.html
 */
public class BezierCurve extends Path {

	private DriveTrainInterface driveTrain = Robot.getRobotConfiguration().getDriveTrain();

	private final double startVelocity;
	private final double endVelocity;
	private final double startLMidpoint;
	private double curveLength = 0;

	private final Point[] pathPoints;
	private final Point[] leftPoints;
	private final Point[] rightPoints;

	private final double robotLength;
	private final Point[] controlPoints;
	private double[] coefficients;

	/**
	 * Creates a new bezier curve
	 * 
	 * @param vCruise
	 *            - the cruise velocity of the robot
	 * @param aMax
	 *            - the maximum acceleration of the robot
	 * @param v0
	 *            - the start velocity
	 * @param v1
	 *            - the end velocity
	 * @param numberOfSteps
	 *            - the amount of points to define the curve, the resolution of the
	 *            curve
	 * @param robotWidth
	 *            - the width of the robot
	 * @param controlPoints
	 *            - the control points that define the robot
	 */
	public BezierCurve(double vCruise, double aMax, double v0, double v1, int numberOfSteps, double robotWidth,
			Point... controlPoints) {
		super(vCruise, aMax);
		this.robotLength = robotWidth;
		this.controlPoints = controlPoints;
		startVelocity = v0;
		endVelocity = v1;
		// The starting average encoder distance
		startLMidpoint = (driveTrain.getWheelPositions().getLeft() + driveTrain.getWheelPositions().getRight()) / 2;

		updateCoefficients();
		pathPoints = getCurvePoints(numberOfSteps, controlPoints);

		rightPoints = offsetPoints(pathPoints, true);
		leftPoints = offsetPoints(pathPoints, false);
	}

	/**
	 * Uses the formula to find the value of nCr
	 * 
	 * @param n
	 * @param r
	 * @return nCr
	 */
	private static double findNumberOfCombination(double n, double r) {
		double nFactorial = factorial(n);
		double rFactorial = factorial(r);
		double nMinusRFactorial = factorial(n - r);

		return nFactorial / (rFactorial * nMinusRFactorial);
	}

	/**
	 * Finds the factorial of any integer or double, d
	 * 
	 * @param d
	 * @return the factorial of d
	 */
	private static double factorial(double d) {
		double r = d - Math.floor(d) + 1;
		for (; d > 1; d -= 1) {
			r *= d;
		}
		return r;
	}

	/**
	 * @param numberOfSteps
	 * @param controlPoints
	 * @return an array of Points that define the curve
	 */
	private Point[] getCurvePoints(int numberOfSteps, Point[] controlPoints) {
		Point[] point2DList = new Point[numberOfSteps + 1];

		for (double i = 0; i <= numberOfSteps; i++) {
			point2DList[(int) i] = getPoint(i / ((double) numberOfSteps), controlPoints);
			if (i != numberOfSteps)
				curveLength += point2DList[(int) i].distance(point2DList[(int) i + 1]);
		}

		return point2DList;
	}

	/**
	 * Updates the coefficients used for calculations
	 */
	private void updateCoefficients() {
		int n = getDegree();
		coefficients = new double[n + 1];
		for (int i = 0; i < coefficients.length; i++) {
			coefficients[i] = findNumberOfCombination(n, i);
		}
	}

	/**
	 * Returns the point on the curve at any percentage on the line, t
	 * 
	 * @param percentage
	 *            - t
	 * @param controlPoints
	 * @return the Point that is at percentage t along the curve
	 */
	private Point getPoint(double percentage, Point[] controlPoints) {
		double xCoordinateAtPercentage = 0;
		double yCoordinateAtPercentage = 0;

		int n = getDegree();

		for (double i = 0; i <= n; i++) {
			double coefficient = coefficients[(int) i];

			double oneMinusT = Math.pow(1 - percentage, n - i);

			double powerOfT = Math.pow(percentage, i);

			Point pointI = controlPoints[(int) i];

			xCoordinateAtPercentage += (coefficient * oneMinusT * powerOfT * pointI.getX());
			yCoordinateAtPercentage += (coefficient * oneMinusT * powerOfT * pointI.getY());
		}

		return new Point(xCoordinateAtPercentage, yCoordinateAtPercentage, getDT(percentage), 0, 0, 0);
	}

	/**
	 * @return the degree of the curve
	 */
	private int getDegree() {
		return controlPoints.length - 1;
	}

	/**
	 * Given the control points defining the curve, find the derivative at any point
	 * on the curve
	 * 
	 * @param t
	 *            - percent along curve
	 * @param controlPoints
	 * @return derivative at point
	 */
	private double getDT(double t) {
		int n = getDegree();
		double dx = 0;
		double dy = 0;
		for (int i = 0; i < n; i++) {
			double coefficient = findNumberOfCombination(n, i) * Math.pow(t, i) * Math.pow(1 - t, n - i);
			dx += coefficient * (n + 1) * (controlPoints[i + 1].getX() - controlPoints[i].getX());
			dy += coefficient * (n + 1) * (controlPoints[i + 1].getY() - controlPoints[i].getY());
		}
		double dt;
		if (t != 1) {
			dt = dy / dx;
		} else {
			dt = (controlPoints[controlPoints.length - 1].getY() - controlPoints[controlPoints.length - 2].getY())
					/ (controlPoints[controlPoints.length - 1].getX() - controlPoints[controlPoints.length - 2].getX());
		}
		System.out.println(t);
		return dt;
	}

	/**
	 * Offsets control points of a curve
	 * 
	 * @param pathPoints
	 * @param isRightSide
	 * @return an array of Points that defines an offset curve
	 */
	private Point[] offsetPoints(Point[] pathPoints, boolean isRightSide) {
		int n = pathPoints.length;
		Point[] offsetPoints = new Point[n];
		for (int i = 0; i < n; i++) {
			double[][] speeds;
			if (i == 0) {
				speeds = new double[][] { { startVelocity, startVelocity }, { 0, startLMidpoint } };
			} else if (i == n - 1) {
				speeds = new double[][] { { endVelocity, endVelocity }, { 0, pathPoints[i - 1].getLMidpoint() } };
			} else {
				speeds = calculateSpeeds(pathPoints[i - 1], pathPoints[i], i);
			}
			if (isRightSide) {
				offsetPoints[i] = pathPoints[i].offsetPerpendicular(pathPoints[i].getDerivative(), robotLength,
						speeds[0][1], speeds[1][0], speeds[1][1]);
			} else {
				offsetPoints[i] = pathPoints[i].offsetPerpendicular(pathPoints[i].getDerivative(), -robotLength,
						speeds[0][0], speeds[1][0], speeds[1][1]);
			}
		}
		return offsetPoints;
	}

	/**
	 * Calculates the velocities and acceleration required to get from one point to
	 * the next
	 * 
	 * @param point
	 * @param point
	 * @param i
	 * @return the wheel velocities acceleration, and lMidpoint to get to the next point
	 */
	private double[][] calculateSpeeds(Point previousPoint, Point point, int i) {

		// When cruising, acceleration is 0
		double acceleration = 0;

		// The change in angle of the robot
		double dAngle = Math.atan(point.getDerivative() - point.getDerivative());

		// The change in distance of the robot sides
		double dLength = point.distance(point);
		double dlLeft = dLength - dAngle * robotLength / 2;
		double dlRight = dLength + dAngle * robotLength / 2;

		// The time required to get to the next point
		double dTime = Math.max(Math.abs(dlLeft), Math.abs(dlRight)) / vCruise;

		// The hypothetical velocity to get to that point
		double velocity = Math.abs(dLength) / dTime;

		// The average encoder distance to the next point
		double lMidpoint = previousPoint.getLMidpoint() + 0.5 * dLength - startLMidpoint;

		double vAccelerating = Math.sqrt(Math.pow(startVelocity, 2) + aMax * Math.abs(lMidpoint));
		double vDecelerating = Math.sqrt(Math.pow(endVelocity, 2) + aMax * Math.abs(curveLength - lMidpoint));

		if (vAccelerating < velocity && vAccelerating < vDecelerating) {
			acceleration = aMax;
			dTime = Math.abs(dLength) / vAccelerating;
		}
		if (vDecelerating < velocity && vDecelerating < vAccelerating) {
			acceleration = -aMax;
			dTime = Math.abs(dLength) / vDecelerating;
		}

		double velocityL = dlLeft / dTime;
		double velocityR = dlRight / dTime;

		return new double[][] { { velocityL, velocityR }, { acceleration, lMidpoint } };
	}

	@Override
	public Point[] getPathPoints() {
		return pathPoints;
	}

	@Override
	public Point[] getLeftPath() {
		return leftPoints;
	}

	@Override
	public Point[] getRightPath() {
		return rightPoints;
	}

	@Override
	public LimitMode getLimitMode() {
		return LimitMode.LimitLinearAcceleration;
	}
}
