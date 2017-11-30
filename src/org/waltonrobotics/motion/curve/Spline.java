package org.waltonrobotics.motion.curve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.waltonrobotics.controller.Point;
import org.waltonrobotics.controller.Vector2;
import org.waltonrobotics.motion.Path;

/**
 * This class creates splines that travel through set points, or "knots", and
 * all information needed to make the robot travel along the path.
 * 
 * @author Russell Newton
 *
 */

public class Spline implements Path {

	public double width = 1;
	
	private List<List<Point>> pathControlPoints;
	private List<List<Point>> leftControlPoints;
	private List<List<Point>> rightControlPoints;
	private double[] dts;
	private int numberOfSteps;

	/**
	 * Create a new spline 
	 * 
	 * @param numberOfSteps, the amount of points generated for the path. Like the resolution
	 * @param knots, the fixed points the spline will travel through
	 */
	public Spline(int numberOfSteps, Point... knots) {
		this.numberOfSteps = numberOfSteps;
		dts = new double[numberOfSteps * (knots.length-1)];
		pathControlPoints = computeControlPoints(knots);
		leftControlPoints = offsetControlPoints(pathControlPoints, false);
		rightControlPoints = offsetControlPoints(pathControlPoints, true);
	}

	/**
	 * Creates the control points required to make cubic bezier curves that
	 * transition between knots Go to
	 * https://www.particleincell.com/2012/bezier-splines/ for further information,
	 * math explanations, and a visual representation
	 */
	private List<List<Point>> computeControlPoints(Point[] knots) {
		int degree = knots.length - 1;
		Point[] points1 = new Point[degree];
		Point[] points2 = new Point[degree];

		/* constants for Thomas Algorithm */
		double[] a = new double[degree + 1];
		double[] b = new double[degree + 1];
		double[] c = new double[degree + 1];
		double[] r_x = new double[degree + 1];
		double[] r_y = new double[degree + 1];

		/* left most segment */
		a[0] = 0;
		b[0] = 2;
		c[0] = 1;
		r_x[0] = knots[0].getX() + 2 * knots[1].getX();
		r_y[0] = knots[0].getY() + 2 * knots[1].getY();

		/* internal segments */
		for (int i = 1; i < degree - 1; i++) {
			a[i] = 1;
			b[i] = 4;
			c[i] = 1;
			r_x[i] = 4 * knots[i].getX() + 2 * knots[i + 1].getX();
			r_y[i] = 4 * knots[i].getY() + 2 * knots[i + 1].getY();
		}

		/* right segment */
		a[degree - 1] = 2;
		b[degree - 1] = 7;
		c[degree - 1] = 0;
		r_x[degree - 1] = 8 * knots[degree - 1].getX() + knots[degree].getX();
		r_y[degree - 1] = 8 * knots[degree - 1].getY() + knots[degree].getY();

		/* solves Ax=b with the Thomas algorithm */
		for (int i = 1; i < degree; i++) {
			double m = a[i] / b[i - 1]; // temporary variable
			b[i] = b[i] - m * c[i - 1];
			r_x[i] = r_x[i] - m * r_x[i - 1];
			r_y[i] = r_y[i] - m * r_y[i - 1];
		}
		points1[degree - 1] = new Point(r_x[degree - 1] / b[degree - 1], r_y[degree - 1] / b[degree - 1]);
		for (int i = degree - 2; i >= 0; --i) {
			points1[i] = new Point((r_x[i] - c[i] * points1[i + 1].getX()) / b[i],
					(r_y[i] - c[i] * points1[i + 1].getY()) / b[i]);
		}

		/* we have p1, now compute p2 */
		for (int i = 0; i < degree - 1; i++) {
			points2[i] = new Point(2 * knots[i + 1].getX() - points1[i + 1].getX(),
					2 * knots[i + 1].getY() - points1[i + 1].getY());
		}

		points2[degree - 1] = new Point(0.5 * (knots[degree].getX() + points1[degree - 1].getX()),
				0.5 * (knots[degree].getX() + points1[degree - 1].getX()));
		List<List<Point>> controlPoints = new ArrayList<>();
		for (int i = 0; i < degree; i++) {
			List<Point> segmentControlPoints = new ArrayList<>();
			Collections.addAll(segmentControlPoints, knots[i], points2[i], points2[i], knots[i + 1]);
			Collections.addAll(controlPoints, segmentControlPoints);
		}

		return controlPoints;
	}

	private List<List<Point>> offsetControlPoints(List<List<Point>> controlPoints, boolean isRightSide) {
		List<List<Point>> offsetControlPoints;
		Point[] offsetKnots = new Point[controlPoints.size() + 1];
		int i = 0;
		for (List<Point> segmentControlPoints : controlPoints) {
			Point CP0 = segmentControlPoints.get(0);
			Point CP1 = segmentControlPoints.get(1);
			Point CP2 = segmentControlPoints.get(2);
			Point CP3 = segmentControlPoints.get(3);
			double dt_CP0 = getDT(0, CP0, CP1, CP2, CP3);
			Point offsetCP0 = CP0.offsetPerpendicular(dt_CP0, isRightSide ? width : -width);
			offsetKnots[i] = offsetCP0;
			i++;
			if (i == controlPoints.size()) {
				double dt_CP3 = getDT(1, CP0, CP1, CP2, CP3);
				offsetKnots[i] = controlPoints.get(controlPoints.size()-1).get(3).offsetPerpendicular(dt_CP3,
						isRightSide ? width : -width);
			}
		}
		offsetControlPoints = computeControlPoints(offsetKnots);
		return offsetControlPoints;
	}

	private double getDT(int t, Point CP0, Point CP1, Point CP2, Point CP3) {
		double r = 1 - t;
		/* Uses the derivative of the cubic formula to find dx and dy, then dt */
		double dx = CP0.getX() * (-3 * r * r) + CP1.getX() * ((3 * r * r) - (6 * r * t))
				+ CP2.getX() * ((-3 * t * t) + (6 * r * t)) + CP3.getX() * (3 * t * t);
		double dy = CP0.getY() * (-3 * r * r) + CP1.getY() * ((3 * r * r) - (6 * r * t))
				+ CP2.getY() * ((-3 * t * t) + (6 * r * t)) + CP3.getY() * (3 * t * t);
		double dt = dy / dx;
		return dt;
	}
	
	private Point[] findPathPoints(List<List<Point>> controlPoints) {
		List<Point> points = new ArrayList<>();
		for(List<Point> segmentControlPoints : controlPoints) {
			BezierCurve segment = new BezierCurve(numberOfSteps, segmentControlPoints.stream().toArray(Point[]::new));
			Collections.addAll(points, segment.getPathPoints());
		}
		return points.stream().toArray(Point[]::new);
	}

	@Override
	public Vector2[] getSpeedVectors() {
		// TODO Do the math stuff
		return null;
	}

	@Override
	public Point[] getPathPoints() {
		return findPathPoints(pathControlPoints);
	}

	@Override
	public Point[] getLeftPath() {
		return findPathPoints(leftControlPoints);
	}

	@Override
	public Point[] getRightPath() {
		return findPathPoints(rightControlPoints);
	}

	@Override
	public double[] getDTsOnPath() {
		return dts;
	}

}
