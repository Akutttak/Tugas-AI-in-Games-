import java.util.*;

public class Java {

    static class Node implements Comparable<Node> {
        int x, y;
        boolean walkable;
        double gCost;
        double hCost;
        Node parent;

        public Node(int x, int y, boolean walkable) {
            this.x = x;
            this.y = y;
            this.walkable = walkable;
        }

        public double getFCost() {
            return gCost + hCost;
        }

        @Override
        public int compareTo(Node other) {
            int cmp = Double.compare(this.getFCost(), other.getFCost());
            if (cmp == 0) {
                return Double.compare(this.hCost, other.hCost);
            }
            return cmp;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return x == node.x && y == node.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    static class Grid {
        int width, height;
        Node[][] nodes;

        public Grid(int width, int height) {
            this.width = width;
            this.height = height;
            nodes = new Node[width][height];
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    nodes[x][y] = new Node(x, y, true);
                }
            }
        }

        public List<Node> getNeighbors(Node node) {
            List<Node> neighbors = new ArrayList<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    int checkX = node.x + dx;
                    int checkY = node.y + dy;

                    if (checkX >= 0 && checkX < width && checkY >= 0 && checkY < height) {
                        if (Math.abs(dx) == 1 && Math.abs(dy) == 1) {
                            if (!nodes[node.x + dx][node.y].walkable || !nodes[node.x][node.y + dy].walkable) {
                                continue;
                            }
                        }
                        neighbors.add(nodes[checkX][checkY]);
                    }
                }
            }
            return neighbors;
        }

        // Method untuk menggambar visualisasi Tile di Konsol
        public void printGrid(Node start, Node target, List<Node> path) {
            Set<Node> pathSet = new HashSet<>();

            if (path != null && !path.isEmpty()) {
                // Hubungkan setiap waypoint hasil smoothing dengan garis tile
                for (int i = 0; i < path.size() - 1; i++) {
                    Node n1 = path.get(i);
                    Node n2 = path.get(i + 1);

                    int x0 = n1.x, y0 = n1.y;
                    int x1 = n2.x, y1 = n2.y;
                    int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
                    int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
                    int err = dx - dy;

                    while (true) {
                        pathSet.add(nodes[x0][y0]);
                        if (x0 == x1 && y0 == y1) break;
                        int e2 = 2 * err;
                        if (e2 > -dy) { err -= dy; x0 += sx; }
                        if (e2 < dx) { err += dx; y0 += sy; }
                    }
                }
            }

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Node current = nodes[x][y];
                    if (current.equals(start)) {
                        System.out.print("S ");
                    } else if (current.equals(target)) {
                        System.out.print("T ");
                    } else if (!current.walkable) {
                        System.out.print("# ");
                    } else if (pathSet.contains(current)) {
                        System.out.print("* "); // Menampilkan tile garis lurus
                    } else {
                        System.out.print(". ");
                    }
                }
                System.out.println();
            }
        }
    }

    public static double getDistanceOctile(Node nodeA, Node nodeB) {
        int dstX = Math.abs(nodeA.x - nodeB.x);
        int dstY = Math.abs(nodeA.y - nodeB.y);
        double D = 1.0;
        double D2 = Math.sqrt(2);
        return D * Math.max(dstX, dstY) + (D2 - D) * Math.min(dstX, dstY);
    }

    static class PathResult {
        List<Node> path;
        int nodesEvaluated;

        public PathResult(List<Node> path, int nodesEvaluated) {
            this.path = path;
            this.nodesEvaluated = nodesEvaluated;
        }
    }

    public static PathResult findPath(Grid grid, Node startNode, Node targetNode, String mode, boolean applyTieBreaking) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Node> closedSet = new HashSet<>();

        for (int x = 0; x < grid.width; x++) {
            for (int y = 0; y < grid.height; y++) {
                grid.nodes[x][y].gCost = Double.MAX_VALUE;
                grid.nodes[x][y].hCost = 0;
                grid.nodes[x][y].parent = null;
            }
        }

        startNode.gCost = 0;
        if (mode.equalsIgnoreCase("A*")) {
            startNode.hCost = getDistanceOctile(startNode, targetNode);
            if (applyTieBreaking) {
                startNode.hCost *= (1.0 + 0.001);
            }
        } else {
            startNode.hCost = 0;
        }

        openSet.add(startNode);
        int nodesEvaluatedCount = 0;

        while (!openSet.isEmpty()) {
            Node currentNode = openSet.poll();
            nodesEvaluatedCount++;

            if (currentNode.equals(targetNode)) {
                return new PathResult(reconstructPath(startNode, targetNode), nodesEvaluatedCount);
            }

            closedSet.add(currentNode);

            for (Node neighbor : grid.getNeighbors(currentNode)) {
                if (!neighbor.walkable || closedSet.contains(neighbor)) {
                    continue;
                }

                double moveCost = (currentNode.x != neighbor.x && currentNode.y != neighbor.y) ? Math.sqrt(2) : 1.0;
                double newCostToNeighbor = currentNode.gCost + moveCost;

                if (newCostToNeighbor < neighbor.gCost || !openSet.contains(neighbor)) {
                    openSet.remove(neighbor); // Fix re-sort PriorityQueue

                    neighbor.gCost = newCostToNeighbor;
                    if (mode.equalsIgnoreCase("A*")) {
                        neighbor.hCost = getDistanceOctile(neighbor, targetNode);
                        if (applyTieBreaking) {
                            neighbor.hCost *= (1.0 + 0.001);
                        }
                    } else {
                        neighbor.hCost = 0;
                    }

                    neighbor.parent = currentNode;
                    openSet.add(neighbor);
                }
            }
        }

        return new PathResult(new ArrayList<>(), nodesEvaluatedCount);
    }

    private static List<Node> reconstructPath(Node startNode, Node endNode) {
        List<Node> path = new ArrayList<>();
        Node currentNode = endNode;
        while (currentNode != null) {
            path.add(currentNode);
            if (currentNode.equals(startNode)) break;
            currentNode = currentNode.parent;
        }
        Collections.reverse(path);
        return path;
    }

    public static boolean hasLineOfSight(Grid grid, Node start, Node end) {
        int x0 = start.x, y0 = start.y;
        int x1 = end.x, y1 = end.y;

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (!grid.nodes[x0][y0].walkable) {
                return false;
            }
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
        return true;
    }

    public static List<Node> smoothPath(Grid grid, List<Node> path) {
        if (path == null || path.size() <= 2) {
            return path;
        }

        List<Node> smoothedPath = new ArrayList<>();
        smoothedPath.add(path.get(0));

        int currentIndex = 0;
        while (currentIndex < path.size() - 1) {
            int furthestVisible = currentIndex + 1;
            for (int nextIndex = path.size() - 1; nextIndex > currentIndex; nextIndex--) {
                if (hasLineOfSight(grid, path.get(currentIndex), path.get(nextIndex))) {
                    furthestVisible = nextIndex;
                    break;
                }
            }
            smoothedPath.add(path.get(furthestVisible));
            currentIndex = furthestVisible;
        }

        return smoothedPath;
    }

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   UJI 1: BENCHMARK DIJKSTRA VS A* (LABIRIN)");
        System.out.println("==================================================");
        
        Grid mazeGrid = new Grid(20, 20);
        for (int y = 0; y < 15; y++) mazeGrid.nodes[5][y].walkable = false;
        for (int y = 5; y < 20; y++) mazeGrid.nodes[10][y].walkable = false;
        for (int y = 0; y < 15; y++) mazeGrid.nodes[15][y].walkable = false;

        Node mazeStart = mazeGrid.nodes[0][0];
        Node mazeTarget = mazeGrid.nodes[19][19];

        PathResult dijkstraResult = findPath(mazeGrid, mazeStart, mazeTarget, "Dijkstra", false);
        PathResult aStarResult = findPath(mazeGrid, mazeStart, mazeTarget, "A*", false);

        System.out.println("Evaluasi Node Dijkstra : " + dijkstraResult.nodesEvaluated);
        System.out.println("Evaluasi Node A* Octile: " + aStarResult.nodesEvaluated);

        double reduction = (1.0 - ((double) aStarResult.nodesEvaluated / dijkstraResult.nodesEvaluated)) * 100;
        System.out.printf("Persentase Reduksi Node  : %.2f%%\n", reduction);
        System.out.println("Target Status (> 60%%)  : " + (reduction > 60 ? "TERCAPAI (PASS)" : "GAGAL (FAIL)"));

        System.out.println("\n--- VISUALISASI TILE LABIRIN (A*) ---");
        System.out.println("Keterangan: S = Start, T = Target, # = Dinding, * = Jalur Rute, . = Tile Kosong\n");
        mazeGrid.printGrid(mazeStart, mazeTarget, aStarResult.path);

        System.out.println("\n==================================================");
        System.out.println("   UJI 2: TIE-BREAKING DI AREA TERBUKA (30x30)");
        System.out.println("==================================================");
        
        Grid openGrid = new Grid(30, 30);
        Node openStart = openGrid.nodes[0][0];
        Node openTarget = openGrid.nodes[29][29];

        PathResult aStarNormal = findPath(openGrid, openStart, openTarget, "A*", false);
        PathResult aStarTieBreak = findPath(openGrid, openStart, openTarget, "A*", true);

        System.out.println("Evaluasi Node A* Standar     : " + aStarNormal.nodesEvaluated);
        System.out.println("Evaluasi Node A* Tie-Breaking: " + aStarTieBreak.nodesEvaluated);

        System.out.println("\n==================================================");
        System.out.println("   UJI 3: IMPLEMENTASI STRING PULLING");
        System.out.println("==================================================");
        
        List<Node> rawZigZagPath = new ArrayList<>();
        rawZigZagPath.add(openGrid.nodes[0][0]);
        rawZigZagPath.add(openGrid.nodes[1][0]);
        rawZigZagPath.add(openGrid.nodes[1][1]);
        rawZigZagPath.add(openGrid.nodes[2][1]);
        rawZigZagPath.add(openGrid.nodes[2][2]);
        rawZigZagPath.add(openGrid.nodes[5][5]);
        rawZigZagPath.add(openGrid.nodes[10][10]);
        rawZigZagPath.add(openGrid.nodes[15][15]);
        rawZigZagPath.add(openGrid.nodes[29][29]);

        List<Node> smoothedPath = smoothPath(openGrid, rawZigZagPath);

        System.out.print("Rute Mentah (Zig-zag) [Count: " + rawZigZagPath.size() + "]: ");
        for (Node n : rawZigZagPath) System.out.print("(" + n.x + "," + n.y + ") ");
        System.out.println();

        System.out.print("Hasil SmoothPath()   [Count: " + smoothedPath.size() + "]: ");
        for (Node n : smoothedPath) System.out.print("(" + n.x + "," + n.y + ") ");
        System.out.println();

        System.out.println("\n--- VISUALISASI TILE STRING PULLING (SMOOTH PATH) ---");
        openGrid.printGrid(openStart, openTarget, smoothedPath);
    }
}