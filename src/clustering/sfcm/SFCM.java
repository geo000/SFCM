/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package clustering.sfcm;

import java.io.IOException;
import java.util.HashSet;
import java.util.Random;

/**
 *
 * @author valerio
 */
public class SFCM {

    private SFCM(){
    }

    public static Object[] run (float [][] X, int k, double tolerance,
                                int randomSeed, int initMode,
                                ClusteringDelegate delegate,
                                double m, long iterations, int stopCriterion,
                                int r, double p, double q, int spatialFunction,
                                int width,
                                boolean testing){

        float [][] V = new float[k][X[0].length];
        float [][] U = new float [X.length][k];

        initializeMatrixes(X, V, U, k, randomSeed, initMode, m);

        Object[] clusteredMatrixes = clusterize(X, U, V, k, tolerance, delegate, 
                                                m, iterations, stopCriterion, r,
                                                p, q, spatialFunction, width,
                                                testing);
        Object[] matrixes = new Object[3];
        matrixes[0] = defuzzyfyClusterMemberships((float [][])clusteredMatrixes[0]);
        matrixes[1] = clusteredMatrixes[1];
        matrixes[2] = clusteredMatrixes[0];
        return matrixes;
    }

    private static Object [] initializeMatrixes(float [][] X, float [][] V, 
                                                float [][] U, int k, 
                                                int randomSeed, int initMode,
                                                double m){
        
        Object[] initedMatrixes = new Object[2];
        double [][] D = null;

        switch(initMode)
        {
            case 0:
                V = randomInitialization(X, V, k, randomSeed);
                D = euclideanDistanceMatrix(X, V, D, m);
                U = updateClusterMembershipMatrix(X, U, V, m, D);
                System.out.println("Random V");
                break;
            case 1:
                V = kMeansPlusPlusInitialization(X, V, k, randomSeed);
                //printMatrix(V);
                D = euclideanDistanceMatrix(X, V, D, m);
                //printMatrix(D);
                U = updateClusterMembershipMatrix(X, U, V, m, D);
                System.out.println("\n\nU\n\n");
                //printMatrix(U);
                System.out.println("K-Means++");
                
                break;
            case 2:
                U = initializeClusterMembershipRandom(U, k, randomSeed);
                System.out.println("Random U");
                break;
            default:
                break;
        }
        return initedMatrixes;
    }

    private static float [][] initializeClusterCenterMatrix(float [][] X,
                                                            float [][] V,
                                                            int k,
                                                            int randomSeed,
                                                            int initMode,
                                                            ClusteringDelegate delegate){

        final long startTime = System.currentTimeMillis();

        switch(initMode)
        {
            case 0:
                V = randomInitialization(X, V, k, randomSeed);
                System.out.println("Random");
                break;
            case 1:
                V = kMeansPlusPlusInitialization(X, V, k, randomSeed);
                System.out.println("K-Means++");
                break;
            default:
                break;
        }

        final long endTime = System.currentTimeMillis();
        //System.out.println("Initialization done in " + (endTime - startTime) + "ms");
        String message = "Initialization done in " + (endTime - startTime) + "ms";
        delegate.updateStatus(message);

        return V;
    }

    private static void printMatrix(float [][] A){
        for (int i = 0; i < A.length; i++)
        {
            for (int j = 0; j < A[0].length; j ++)
            {
                System.out.print(A[i][j] + " ");
            }
            System.out.println("");
        }
    }

    private static void printMatrix(double [][] A){
        for (int i = 0; i < A.length; i++)
        {
            for (int j = 0; j < A[0].length; j ++)
            {
                System.out.print(A[i][j] + " ");
            }
            System.out.println("");
        }
    }

    private static float [][] initializeClusterMembershipRandom(float [][] U, int k, int randomSeed){

        int nClusters = U[0].length;
        final Random random = createRandom(randomSeed);
        for (int i = 0; i < U.length; i++)
        {
            float sum = 0;
            for (int j = 0; j < nClusters; j++)
            {
                U[i][j] =  random.nextFloat();
                sum += U[i][j];
            }
            for (int j = 0; j < nClusters; j++)
            {
                U[i][j] /= sum;
            }
        }
        //printMatrix(U);
        return U;
    }

    private static float[][] randomInitialization(float [][] X, float [][] V, int k, int randomSeed) {

        final Random random = createRandom(randomSeed);
        final int nbPixels = X.length;
        final int nFeatures = X[0].length;

        final HashSet<Integer> centerLocations = new HashSet<Integer>();
        int clusterCreated = 0;

        while (clusterCreated < k)
        {
            final int clusterCandidate = random.nextInt(nbPixels);

            /* Check if it has already been extracted */
            if (!centerLocations.contains(clusterCandidate))
            {
                /* Let's copy the pixel values in the centroid matrix */
                System.arraycopy(X[clusterCandidate], 0, V[clusterCreated], 0, nFeatures);
                centerLocations.add(clusterCandidate);
                clusterCreated++;
            }
        }
        return V;
    }

    private static float[][] kMeansPlusPlusInitialization(float [][] X, float [][] V, int k, int randomSeed) {

        final Random random = createRandom(randomSeed);
        final int nbPixels = X.length;
        final int nFeatures = X[0].length;

        // Location of pixels used as cluster centers
        final HashSet<Integer> centerLocation = new HashSet<Integer>();
        int clusterCreated = 0;
        // Choose one center uniformly at random from among pixels
        {
            final int p = random.nextInt(nbPixels);
            centerLocation.add(p);
            System.arraycopy(X[p], 0, V[0], 0, nFeatures);
            clusterCreated++;
        }

        final double[] dp2 = new double[nbPixels];
        while (clusterCreated < k) {
            // For each data point p compute D(p), the distance between p and the nearest center that
            // has already been chosen.
            double sum = 0;

            for (int offset = 0; offset < nbPixels; offset++) {

                if (centerLocation.contains(offset)) {
                    continue;
                }
                // Distance to closest cluster

                final float[] v = X[offset];
                final int cci = closestCluster(v, V, clusterCreated);
                final double d = euclideanDistance(v, V[cci]);
                sum += d * d;
                dp2[offset] = sum;
            }


            /* Add one new data point at random as a new center, using a weighted probability distribution where
             * a point p is chosen with probability proportional to D(p)^2 */
            final double r = random.nextDouble() * sum;
            for (int offset = 0; offset < nbPixels; offset++) {

                /* Test that this is not a repeat of already selected center */
                if (centerLocation.contains(offset)) {
                    continue;
                }

                if (dp2[offset] >= r)
                {
                    centerLocation.add(offset);
                    System.arraycopy(X[offset], 0, V[clusterCreated], 0, nFeatures);
                    clusterCreated++;
                    break;
                }
            }
        }

        return V;
    }

    private static Random createRandom(int randomSeed) {
//        return config.isRandomizationSeedEnabled()
//                ? new Random(config.getRandomizationSeed())
//                : new Random();
        return new Random(randomSeed);
    }

    /**
     * Return index of the closest cluster to point <code>x</code>.
     *
     * @param x              point features.
     * @param clusterCenters cluster centers features.
     * @return index of the closest cluster
     */
    private static int closestCluster(final float[] x, final float[][] clusterCenters, int k) {

        double minDistance = Double.MAX_VALUE;
        int closestCluster = -1;

        for (int i = 0; i < k; i++)
        {
            final float[] clusterCenter = clusterCenters[i];
            final double d = euclideanDistance(clusterCenter, x);
            if (d < minDistance)
            {
                minDistance = d;
                closestCluster = i;
            }
        }

        return closestCluster;
    }


    /**
     * Distance between points <code>a</code> and <code>b</code>.
     * This is the squared rooted version
     * @param a first point.
     * @param b second point.
     * @return distance.
     */
    private static double euclideanDistance(final float[] a, final float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            final double d = a[i] - b[i];
            sum += d * d;
        }
        //return sum;
        return Math.sqrt(sum);
    }

    private static double [][] euclideanDistanceMatrix(final float [][] A, final float [][] B,
                                                            double [][] D, double m){

        if (D == null)
        {
            System.out.println("Distance Matrix allocated");
            D = new double[A.length][B.length + 1];
        }

        double exp = (1 / (m - 1));
        int nDims = A[0].length;

        for (int i = 0; i < A.length; i++)
        {
            D[i][B.length] = 0.0;
            for (int j = 0; j < B.length; j++)
            {
                double sum = 0;
                for (int k = 0; k < nDims; k++)
                {
                    final double d = A[i][k] - B[j][k];
                    sum += d * d;
                }

                //D[i][j] = (float)Math.sqrt(sum);
                D[i][j] = sum;
                //System.out.println("D"+ i + j + " " + D[i][j]);
                if (D[i][j] == 0.0)
                {
                    D[i][B.length] = -1;
                }
                else
                {
                    if (D[i][B.length] != -1)
                    {
                        D[i][B.length] += Math.pow(1 / D[i][j], exp);
                    }
                }
            }
        }
        return D;
    }

//    private static float euclideanDistance(final int a, final int b, float [][] D){
//
//        return D[a][b];
//    }

    private static float [][] computeExponentialMembership(float [][] U, float [][] Um, double m){

        int nClusters = U[0].length;
        if (Um == null)
        {
            System.out.println("Allocating U^m matrix");
            Um = new float[U.length][nClusters];
        }

        for (int i = 0; i < U.length; i++)
        {
            for (int j = 0; j < nClusters; j++)
            {
                Um[i][j] = (float) Math.pow(U[i][j], m);
                if ( Float.isNaN(Um[i][j]))
                {
                    throw new IllegalArgumentException("Nan found "+ U[i][j] + " m " + m);
                }
            }
        }
        return Um;
    }

    private static float computeObjectiveFunction(float [][] X, float [][] Um, float [][]V, double [][] D){

        float objF = 0;
        for(int i = 0; i < X.length; i++)
        {
             for(int j = 0; j < V.length; j++)
               {
                //float distancePixelToCluster = euclideanDistance(i, j, D);
                double distancePixelToCluster = D[i][j];
                //System.out.println("Distance " + distancePixelToCluster);
                objF += distancePixelToCluster * Um[i][j];
                //System.out.println("obj " + objF);
               }
        }
        return objF;
    }

    private static float[][] updateClusterMembershipMatrix(float [][] X, float [][] U, float [][] V,
                                                           double m, double [][] D){

        int nClusters = V.length;
        double exp = (1.0 / (m - 1.0));
        for(int i = 0; i < X.length; i++)
        {
            int count = 0;

            for(int j = 0; j < nClusters; j++)
            {
                    //float num = euclideanDistance(i, j, D);
                    double distance = D[i][j];
                    if(distance != 0.0)
                    {
//                                            //  sum of distances from this data point to all clusters.
//                        float sumTerms = 0;
//                        for(int k = 0; k < V.length; k++)
//                        {
//                            //float thisDistance = euclideanDistance(i,k, D);
//                            float thisDistance = D[i][k];
//
//                            //sumTerms += Math.pow(num / thisDistance, (2f / (m - 1f)));
//                            sumTerms += Math.pow(num / thisDistance, (1f / (m - 1f)));
////                            if (thisDistance == 0.0f)
////                            {
////                                System.out.println("Ouch " + i + " " + k + " " + j + " " +
////                                        Math.pow(num / thisDistance, (2f / (m - 1f))));
////                            }
//
////                                             if ( Float.isNaN(thisDistance))
////                    {
////                        throw new IllegalArgumentException("thisDistance"+ num + " m " + m);
////                    }
////                                             if ( Float.isNaN(sumTerms))
////                    {
////                        throw new IllegalArgumentException("sumterms "+ thisDistance + "
////                            d " + num+ " j " + j + " k " + k);
////                    }
//                        }
//
//                        //sumTerms = (float)(Math.pow(num, (1f / (m - 1f)))) / D[i][V.length];
//                        //System.out.println(D[i][V.length] + " " + sumTerms + " " + (float)(Math.pow(num, (1f / (m - 1f)))));
//                        U[i][j] = (1f / sumTerms);
                        double denominatorSum = D[i][nClusters];
                        if (denominatorSum != -1)
                        {
                            
                            double numerator = Math.pow(distance, exp);
                            //U[i][j] = 1 / (numerator / denominator);
                            U[i][j] = (float) (1f / (numerator * denominatorSum));
                            if ( Float.isNaN(U[i][j]))
                            {
                                throw new IllegalArgumentException("Nan found " + " m " + m + " num " + numerator
                                        + " den " + denominatorSum + " dis " + distance + " exp " + exp);
                            }
                        }
                        else
                        {
                            U[i][j] = 0;
                        }
                    }
                    else
                    {
                        count++;
//                        float sum = 0;
//                        for (int h = 0; h < V.length; h++)
//                        {
//                            if (h != j)
//                                sum += U[i][h];
//                        }
//                        System.out.println("Sum " + sum);
                        U[i][j] = 1.0f;
                    }

                    if ( Float.isNaN(U[i][j]))
                    {
                        throw new IllegalArgumentException("Nan found " + " m " + m);
                    }
            }

            if (count > 1)
            {
                for(int j = 0; j < V.length; j++)
                {
                    if (U[i][j] == 1.0f)
                    {
                        U[i][j] = 1f / count;
                    }
                }
            }
        }
        return U;

    }

    private static float[][] updateClusterCenterMatrix(float [][] X, float [][] Um, float [][] V){


        double numerator = 0;
        double denominator = 0;

        int nClusters = V.length;
        int nFeatures = X[0].length;

        for (int i = 0; i < nClusters; i++)
        {

            for (int j = 0; j < nFeatures; j++)
            {
                numerator = 0;
                denominator = 0;
                for(int k = 0; k < X.length; k++)
                {
                    /// trovare tutti i pixel di 1 determinato cluster k
                    float x[] = X[k];
                    numerator += Um[k][i] * x[j];
                    denominator += Um[k][i];
                    //System.out.println("Um" + k + i + " " + Um[k][i] + " x " + x[j]);
                    //System.out.println("N " + numerator + " D " + denominator);
                }
                /// cluster x caratteristiche. ovvero V
                V[i][j] = (float) (numerator / denominator);
                if ( Float.isNaN(V[i][j]))
                {
                    throw new IllegalArgumentException("nume "+ numerator + " denom " + denominator);
                }
                //System.out.println("V" + i + j + "n " + numerator + " d " + denominator  );
            }
        }
        return V;
    }

    private static int [][] defuzzyfyClusterMemberships(float [][] U){

        int nClusters = U[0].length;
        int [][] defU = new int [U.length][nClusters];

        for (int i = 0; i < U.length; i++)
        {

            float max = -1;
            int maxPos = -1;
            for(int j = 0; j < nClusters; j++)
            {
                if ( U[i][j] > max)
                {
                    max = U[i][j];
                    maxPos = j;
                }
                
            }

            defU[i][maxPos] = 1;
        }
        
        return defU;
    }

    private static void matrixCopy(float [][] A, float [][] B){

        int nDim = A[0].length;
        for (int i = 0; i < A.length; ++i)
        {
            System.arraycopy(A[i], 0, B[i], 0, nDim);
        }
    }

    private static float frobeniusNorm(float [][] A, float [][] B){
        
        float d = 0;
        float sum = 0;
        int nDim = A[0].length;
        for(int i = 0; i < A.length; i++)
        {
            for(int j = 0; j < nDim; j++)
            {
                 d = A[i][j] - B[i][j];
                 sum += d*d;
            }
        }
        return (float) Math.sqrt(sum);
    }

    private static float maxNorm(float [][] A, float [][] B){

        float max = 0;
        float abs = 0;
        int nDim = A[0].length;
        for(int i = 0; i < A.length; i++)
        {
            for(int j = 0; j < nDim; j++)
            {
                abs = Math.abs(A[i][j] - B[i][j]);
                if (abs > max)
                {
                    max = abs;
                }
            }
        }
        return max;
    }

    private static float checkConvergence(float [][] U, float [][] oldU,
                                          float [][] V, float [][] oldV, int criterion){
        float diff = 0;

        switch(criterion)
        {
            case 0:
                diff = frobeniusNorm(U, oldU);
                break;
            case 1:
                diff = frobeniusNorm(V, oldV);
                break;
            case 2:
                diff = maxNorm(U, oldU);
                break;
            case 3:
                diff = maxNorm(V, oldV);
                break;
            default:
                break;
        }
        return diff;
    }

    private static float [][] updateMembershipsWithSpatialInformation(float [][] U, int r,
                                                                      double p, double q,
                                                                      int spatialFunctionType,
                                                                      int cols, int [][] xy,
                                                                      int [][] offset,
                                                                      double [][] uPhQ){

        //float[][] Uupdate = new float[U.length][U[0].length];
        int nClusters = U[0].length;
        if(uPhQ == null)
        {
            uPhQ = new double[U.length][nClusters + 1];
        }

        int rows = U.length / cols;

        for(int i = 0; i < U.length; i++)
        {
            double numerator = 0;
            uPhQ[i][nClusters] = 0;

            for(int j = 0; j < nClusters; j++)
            {

                int row = xy[i][0];
                int col = xy[i][1];
                float h = 0;

                for(int ry = -r; ry <= r; ry++)
                {
                    int y = row + ry;
                    if (y >= 0 && y < rows)
                    {
                        for (int rx = -r; rx <= r; rx++)
                        {
                            int x = col + rx;
                            if (x >= 0 && x < cols)
                            {
                                int elem = offset[y][x];

                                if(spatialFunctionType == 0)
                                {
                                    h += U[elem][j];
                                }
                                else
                                {
                                    boolean exit = false;
                                    for(int k = 0; k < nClusters && !exit; k++)
                                    {
                                        if(U[elem][j] < U[elem][k])
                                        {
                                            exit = true;
                                        }
                                    }

                                    if(exit == false)
                                    {
                                        h += 1;
                                    }
                                }
                            }
                        }
                    }
                }

                double uPTimeshQ = Math.pow(U[i][j], p) *
                                   Math.pow(h, q);
                uPhQ[i][j] = uPTimeshQ;
                uPhQ[i][nClusters] += uPTimeshQ;

//                int row = i / cols;
//                int col = i - row * cols;
//
//                numerator = (float) ((Math.pow(U[i][j], p)) *
//                        Math.pow(functionH(U, r, row, col, j, rows, cols, spatialFunctionType), q));
//
//                float denominator = 0;
//
//                for (int k = 0; k < U[0].length; k++)
//                {
//                    denominator += ((Math.pow(U[i][k], p)) *
//                            (Math.pow(functionH(U, r, row, col, k, rows, cols, spatialFunctionType), q)));
//                }
//
//                Uupdate[i][j] = numerator / denominator;
            }
        }

        for(int i = 0; i < U.length; i++)
        {
            double denominatorSum = uPhQ[i][nClusters];
            for(int j = 0; j < nClusters; j++)
            {
                U[i][j] = (float) (uPhQ[i][j] / denominatorSum);
            }
        }
        //U = Uupdate;
        return U;
    }

    private static int [][] precomputeMatrixForm(int offset, int width){
        int [][] matrixForm = new int [offset][2];
        for (int i = 0; i < offset; i++)
        {
            matrixForm[i][0] = i / width;
            matrixForm[i][1] = i - matrixForm[i][0] * width;
        }
        return matrixForm;
    }

    private static int [][] precomputeArrayForm(int rows, int columns){
        int [][] arrayForm = new int [rows][columns];
        for(int i = 0; i < rows; i++)
        {
            for (int j = 0; j < columns; j++)
            {
                arrayForm[i][j] = j + i * columns;
            }
        }
        return arrayForm;
    }

    private static Object[] clusterize(float [][] X, float [][] U, float [][] V,
                                       int k, double tolerance, ClusteringDelegate delegate,
                                       double m, long iterations, int stopCriterion,
                                       int r, double p, double q, int spatialFunction,
                                       int width, boolean testing){
        boolean converged = false;
        long count = 0;

        Object[] resultMatrixes = new Object[2];
        final int nFeatures = X[0].length;
        
        double [][] D = null;
        float [][] Um = null;
        Um = computeExponentialMembership(U, Um, m);
        System.out.println("\n\nUM\n\n");
        //printMatrix(Um);

        int [][] xy = precomputeMatrixForm(X.length, width);
        int [][] offset = precomputeArrayForm(X.length / width, width);
        double [][] uPhQ = null;

        float [][] oldU = null;
        if (stopCriterion == 0 || stopCriterion == 2)
        {
            oldU = new float [U.length][k];
        }

        float [][] oldV = null;
        if (stopCriterion == 1 || stopCriterion == 3)
        {
            oldV = new float [V.length][nFeatures];
        }

        float oldJ = 0; //computeObjectiveFunction(X, U, V, D);
        float distance = Float.MAX_VALUE;

        while (count < iterations && distance > tolerance)
        {

            V = updateClusterCenterMatrix(X, Um, V);

//            if (count < 2){
//                System.out.println("\n\nV\n\n " + count);
//                printMatrix(V);
//            }

            D = euclideanDistanceMatrix(X, V, D, m);
//            if (count < 2){
//                System.out.println("\n\nD\n\n " + count);
//                printMatrix(D);
//            }
            
            U = updateClusterMembershipMatrix(X, U, V, m, D);
//            if (count < 2){
//                System.out.println("\n\nU\n\n " + count);
//                printMatrix(U);
//            }

            if ((r != 0) && (p != 1.0 || q != 0.0))
            {
                U = updateMembershipsWithSpatialInformation(U, r, p, q, spatialFunction,
                                                        width, xy, offset, uPhQ);
            }

            Um = computeExponentialMembership(U, Um, m);

            if (!testing)
            {
                distance = checkConvergence(U, oldU, V, oldV, stopCriterion);
            }
            

//            if (diffJ < tolerance)
//            {
//                converged = true;
//            }
//            else
//            {

//            }

            ++count;

            if (testing)
            {
                float diffJ, diffU, diffV;
                float newJ = computeObjectiveFunction(X, Um, V, D);
                diffJ = Math.abs(newJ - oldJ);
                oldJ = newJ;
                diffU = maxNorm(U, oldU);
                diffV = maxNorm(V, oldV);
                try
                {
                //delegate.updateStatus(null, null, count, diffJ);
                    delegate.updateStatus(null, null, count, diffJ, diffU, diffV);
                }
                catch (IOException ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                delegate.updateStatus(null, null, count, distance);
            }

            if (stopCriterion == 0 || stopCriterion == 2)
            {
                matrixCopy(U, oldU);
            }

            if (stopCriterion == 1 || stopCriterion == 3)
            {
                matrixCopy(V, oldV);
            }
            

        }

        //U = computeClusterMembership(X, U, V, k);
        resultMatrixes[0] = U;
        resultMatrixes[1] = V;
        return resultMatrixes;
    }
}
