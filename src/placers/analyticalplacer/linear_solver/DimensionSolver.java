package placers.analyticalplacer.linear_solver;

public interface DimensionSolver {
    void addConnection(
            boolean fixed1, int index1, double coordinate1,
            boolean fixed2, int index2, double coordinate2,
            double weightMultiplier);
    
    void solve();
}