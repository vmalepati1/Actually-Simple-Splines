package org.waltonrobotics.dynamicMotion;

import static org.waltonrobotics.motion.BezierCurve.gaussLegendreHashMap;
import static org.waltonrobotics.util.Helper.calculateCoefficients;

import java.util.Arrays;
import java.util.List;
import org.waltonrobotics.controller.PathData;
import org.waltonrobotics.controller.Pose;
import org.waltonrobotics.motion.BezierCurve.Key;
import org.waltonrobotics.util.GaussLegendre;

/**
 * Everything about Bezier Curves https://pomax.github.io/bezierinfo/ http://ttuadvancedrobotics.wikidot.com/trajectory-planning-for-point-to-point-motion
 */
public class DynamicBezierCurve extends DynamicPath {

  private final double startVelocity;
  private final double endVelocity;
  private final int degree;
  public double time;
  private int[] coefficients;
  private double curveLength;
  private double startLCenter;

  /**
   * This constructor is used with the splines, but feel free to use it when creating your own motions
   *
   * @param vCruise - the cruise velocity of the robot
   * @param aMax - the maximum acceleration of the robot
   * @param startVelocity - the start velocity
   * @param endVelocity - the end velocity
   * @param isBackwards - whether or not to move the robot backwards
   * @param controlPoints - the control points that define the curve
   */
  public DynamicBezierCurve(double vCruise, double aMax, double startVelocity, double endVelocity,
      boolean isBackwards, List<Pose> controlPoints) {
    super(vCruise, aMax, isBackwards, controlPoints);
    this.startVelocity = startVelocity;
    this.endVelocity = endVelocity;
    // The starting average encoder distance should always be 0

    degree = getKeyPoints().size() - 1;
    coefficients = calculateCoefficients(degree);
//    curveLength = computeArcLength();
    curveLength = computeArcLength();
    time = computeTime();

//    long startTime = System.nanoTime();
//    computeArcLength(100, 0.001, 0.002);
//    long endTime = System.nanoTime();
//    System.out.println((endTime - startTime));
//    System.out.println((endTime - startTime) / 1000000.0);
//    System.out.println(curveLength);
//    startTime = System.nanoTime();
//    computeArcLengthSampling(10, 0., 0.002);
////		System.out.println();
//    endTime = System.nanoTime();
//    System.out.println(endTime - startTime);
//    System.out.println((endTime - startTime) / 1000000.0);
//    System.out.println(curveLength);
  }

  public DynamicBezierCurve(double vCruise, double aMax, double startVelocity, double endVelocity,
      boolean isBackwards,
      Pose... controlPoints) {
    this(vCruise, aMax, startVelocity, endVelocity, isBackwards, Arrays.asList(controlPoints));
  }

  public double computeArcLengthSampling(double lower, double upper) {
    return computeArcLengthSampling(1000, lower, upper);
  }

  /**
   * Uses sampling (creating multiple points and summing the distance between them) to calculate the length of the path
   *
   * @param numberOfPoints the number of points to create
   * @param lower the lower bound [0,1]
   * @param upper the upper bound [0, 1]
   * @return the path length
   */
  public double computeArcLengthSampling(int numberOfPoints, double lower, double upper) {

    double distance = 0;
    Pose previous = getPoint(lower);

    double increment = ((upper - lower) / numberOfPoints);
    for (int i = 1; i <= numberOfPoints; i++) {

      Pose point = getPoint((i * increment) + lower);
      distance += point.distance(previous);
      previous = point;
    }

    return distance;
  }

  /**
   * Calculates how much time it would take to complete the path given its length acceleration and target velocity
   */
  private double computeTime() {
    double accelerationTime = calculateTime(startVelocity, getVCruise(), getAMax());
    double accelDistance = distance(startVelocity, getAMax(), accelerationTime);

    double decelerationTime = calculateTime(getVCruise(), endVelocity, -getAMax());
    double decelDistance = distance(getVCruise(), -getAMax(), accelerationTime);

    if (accelDistance + decelDistance > curveLength) {
      return calculateTime(startVelocity, endVelocity, getAMax());
    } else {
      double cruiseTime = (curveLength - (accelDistance + decelDistance)) / getVCruise();

      return accelerationTime + cruiseTime + decelerationTime;
    }
  }

  private double calculateTime(double startVelocity, double endVelocity, double acceleration) {
    return (endVelocity - startVelocity) / acceleration;
  }

  private double distance(double startVelocity, double constantAcceleration, double time) {
    return startVelocity * time + (constantAcceleration * time * time) / 2;
  }

  public double computeArcLength() {
    return computeArcLength(16, 0, 1);
  }

  public double computeArcLength(int n) {

    return computeArcLength(n, 0, 1);
  }

  public double computeArcLength(double lowerBound, double upperBound) {
    return computeArcLength(16, lowerBound, upperBound);
  }

  /**
   * Uses the Gauss Legendre integration to approximate the arc length of the Bezier curve. This is the fastest
   * technique (faster than sampling) when having a large path and shows the most accurate results
   *
   * @param n the number of integral strips 2+ more means better accuracy
   * @param lowerBound the lower bound to integrate (inclusive) [0,1]
   * @param upperBound the upper bound to integrate (inclusive) [0,1]
   * @return the arc length of the Bezier curve of t range of [lowerBound, upperBound]
   */
  public double computeArcLength(int n, double lowerBound, double upperBound) {
    GaussLegendre gl;

    Key key = new Key(n, upperBound, lowerBound);
    if (gaussLegendreHashMap.containsKey(key)) {
      gl = gaussLegendreHashMap.get(key);
    } else {
      gl = new GaussLegendre(n, lowerBound, upperBound);
      gaussLegendreHashMap.put(key, gl);
    }

    double[] t = gl.getNodes();
    double[] C = gl.getWeights();

    double sum = 0;

    for (int i = 0; i < t.length; i++) {

      Pose point = getDerivative(t[i]);
      sum += C[i] * Math.hypot(point.getX(), point.getY());
    }

    return sum;
  }

  public double getStartVelocity() {
    return startVelocity;
  }

  public double getEndVelocity() {
    return endVelocity;
  }

  public int getDegree() {
    return degree;
  }

  public double getCurveLength() {
    return curveLength;
  }

  public double getTime() {
    return time;
  }

  @Override
  public PathData createPathData(PathData previousPathData, double percentage) {
    Pose centerPoint = getPoint(percentage);

    PathData pathData;
//		pathData= calculateData(startAverageEncoderLength, previousPathData, centerPoint);
    pathData = new PathData(centerPoint);
    return pathData;
  }

  /**
   * Gets the derivative of point at value percentage
   */
  private Pose getDerivative(double percentage) {
    double dx = 0;
    double dy = 0;

    if (percentage == 1.0) {

      int last = getKeyPoints().size() - 1;

      dx = getKeyPoints().get(last).getX()
          - getKeyPoints().get(last - 1).getX();
      dy = getKeyPoints().get(last).getY()
          - getKeyPoints().get(last - 1).getY();
    } else {

      int[] coefficients = calculateCoefficients(degree - 1);

      for (int i = 0; i < degree; i++) {

        Pose pointI = getKeyPoints().get(i);

        double multiplier =
            coefficients[i] * StrictMath.pow(1 - percentage, ((degree - 1) - i)) * StrictMath
                .pow(percentage, (double) i);

        Pose nextPointI = getKeyPoints().get(i + 1);

        dx += (multiplier = multiplier * (degree)) * (nextPointI.getX() - pointI.getX());
        dy += multiplier * (nextPointI.getY() - pointI.getY());
      }
    }

    double angle = StrictMath.atan2(dy, dx);

    if (isBackwards()) {
      angle += Math.PI;
    }
    angle %= (2 * Math.PI);

    return new Pose(dx, dy, angle);
  }

  private Pose getPoint(double percentage) {
    return getPoint(degree, percentage);
  }

  /**
   * @param percentage - t
   * @return the Pose that is at percentage t along the curve
   */
  private Pose getPoint(int degree, double percentage) {
    double xCoordinateAtPercentage = 0;
    double yCoordinateAtPercentage = 0;

    double dx = 0;
    double dy = 0;

    int[] derivativeCoefficients = calculateCoefficients(degree - 1);

    for (int i = 0; i <= degree; i++) {

      Pose pointI = getKeyPoints().get(i);

      double multiplier = coefficients[i] *
          StrictMath.pow(1 - percentage, (degree - i)) *
          StrictMath.pow(percentage, (double) i);

      xCoordinateAtPercentage += (multiplier * pointI.getX());
      yCoordinateAtPercentage += (multiplier * pointI.getY());

      if (percentage != 1 && i < degree) {
        Pose nextPointI = getKeyPoints().get(i + 1);

        multiplier = derivativeCoefficients[i] *
            StrictMath.pow(1 - percentage, ((degree - 1) - i)) *
            StrictMath.pow(percentage, (double) i) * degree;

        dx += multiplier * (nextPointI.getX() - pointI.getX());
        dy += multiplier * (nextPointI.getY() - pointI.getY());
      }
    }

    if (percentage == 1.0) {
      int last = getKeyPoints().size() - 1;

      dx = getKeyPoints().get(last).getX()
          - getKeyPoints().get(last - 1).getX();
      dy = getKeyPoints().get(last).getY()
          - getKeyPoints().get(last - 1).getY();
    }

    double angle = StrictMath.atan2(dy, dx);

    if (isBackwards()) {
      angle += Math.PI;
    }
    angle %= (2 * Math.PI);

    return new Pose(xCoordinateAtPercentage, yCoordinateAtPercentage, angle);
  }

  public void setStartLCenter(double startLCenter) {
    this.startLCenter = startLCenter;
  }

  /**
   * @return a new PathData from the calculations
   */
  public PathData calculateData(double percentage) {
    double distanceAtPercentage = computeArcLength(0, percentage);
    System.out.println(distanceAtPercentage);
    double timeAtPercentage = computeTimeToPosition(distanceAtPercentage);
    System.out.println(timeAtPercentage);
    return null;
  }

  private double calculateTimeConstantAcceleration(double startVelocity, double acceleration, double distance) {
    return (-startVelocity + Math.sqrt((startVelocity * startVelocity) - (2 * acceleration * distance))) / acceleration;
  }

  private double computeTimeToPosition(double distanceAtPercentage) {
    double accelerationTime = calculateTime(startVelocity, getVCruise(), getAMax());
    double accelDistance = distance(startVelocity, getAMax(), accelerationTime);

    if (distanceAtPercentage <= accelDistance) {
      return calculateTimeConstantAcceleration(startVelocity, getAMax(), distanceAtPercentage);
    }
//    else {
//      double decelerationTime = calculateTime(getVCruise(), endVelocity, -getAMax());
//      double decelDistance = distance(getVCruise(), -getAMax(), accelerationTime);
//
//      if (accelDistance + decelDistance > curveLength) {
//        return calculateTime(startVelocity, endVelocity, getAMax());
//      } else {
//        double cruiseTime = (curveLength - (accelDistance + decelDistance)) / getVCruise();
//
//        return accelerationTime + cruiseTime + decelerationTime;
//      }
//    }
    return 0;
  }
}