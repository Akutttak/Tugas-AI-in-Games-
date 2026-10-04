import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

public class App {
    private static final double DELTA_TIME = 1.0 / 60.0;
    private static final Color BACKGROUND = new Color(18, 26, 25);
    private static final Color PANEL = new Color(27, 37, 35);
    private static final Color PANEL_LIGHT = new Color(37, 49, 46);
    private static final Color TEXT = new Color(236, 241, 231);
    private static final Color MUTED = new Color(151, 169, 159);
    private static final Color MINT = new Color(112, 230, 177);
    private static final Color CORAL = new Color(255, 137, 108);
    private static final Color GOLD = new Color(248, 197, 92);
    private static final Color BLUE = new Color(112, 190, 235);

    private enum Mode {
        ARRIVE("Arrive"), ORBIT("Orbiting"), WANDER("Wander"), PATH("Path Following");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class Vector2 {
        final double x;
        final double y;

        Vector2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        Vector2 add(Vector2 other) {
            return new Vector2(x + other.x, y + other.y);
        }

        Vector2 subtract(Vector2 other) {
            return new Vector2(x - other.x, y - other.y);
        }

        Vector2 multiply(double scalar) {
            return new Vector2(x * scalar, y * scalar);
        }

        double magnitude() {
            return Math.sqrt(x * x + y * y);
        }

        Vector2 normalized() {
            double length = magnitude();
            return length == 0 ? new Vector2(0, 0) : multiply(1.0 / length);
        }

        Vector2 limited(double maximum) {
            double length = magnitude();
            return length > maximum ? multiply(maximum / length) : this;
        }
    }

    private static class Agent {
        Vector2 position;
        Vector2 velocity;
        double maxSpeed;
        double maxForce;

        Agent(Vector2 position, Vector2 velocity, double maxSpeed, double maxForce) {
            this.position = position;
            this.velocity = velocity;
            this.maxSpeed = maxSpeed;
            this.maxForce = maxForce;
        }

        Vector2 seek(Vector2 target) {
            Vector2 desired = target.subtract(position).normalized().multiply(maxSpeed);
            return desired.subtract(velocity).limited(maxForce);
        }

        Vector2 arrive(Vector2 target, double slowingRadius) {
            Vector2 offset = target.subtract(position);
            double distance = offset.magnitude();
            if (distance == 0) {
                return velocity.multiply(-1).limited(maxForce);
            }
            double targetSpeed = maxSpeed * Math.min(distance / Math.max(slowingRadius, 0.01), 1.0);
            Vector2 desired = offset.multiply(targetSpeed / distance);
            return desired.subtract(velocity).limited(maxForce);
        }

    }

    private static class Simulation {
        private final List<Vector2> route = List.of(
                new Vector2(14, 72), new Vector2(32, 72), new Vector2(45, 58),
                new Vector2(59, 58), new Vector2(78, 31));
        private final List<Vector2> trail = new ArrayList<>();
        private final Random random = new Random(9);
        private final Vector2 arriveTarget = new Vector2(74, 50);
        private final Vector2 orbitCenter = new Vector2(50, 50);
        private Mode mode = Mode.ARRIVE;
        private Agent agent;
        private Vector2 wanderCenter = new Vector2(0, 0);
        private Vector2 wanderTarget = new Vector2(0, 0);
        private double wanderAngle;
        private int waypointIndex;
        private long frame;

        Simulation() {
            reset();
        }

        void setMode(Mode mode) {
            this.mode = mode;
            reset();
        }

        private void reset() {
            frame = 0;
            waypointIndex = 1;
            trail.clear();
            wanderAngle = 0;
            switch (mode) {
                case ARRIVE -> agent = new Agent(new Vector2(16, 50), new Vector2(10, 0), 10, 1);
                case ORBIT -> agent = new Agent(new Vector2(56, 50), new Vector2(0, 4), 10, 15);
                case WANDER -> agent = new Agent(new Vector2(50, 50), new Vector2(4, 0), 10, 15);
                case PATH -> agent = new Agent(route.get(0), new Vector2(0, 0), 10, 15);
            }
            trail.add(agent.position);
            updateWanderTargets(8, 2);
        }

        void update(double delta, double maxSpeed, double maxForce, double slowingRadius,
                double wanderRadius, double orbitRadius) {
            agent.maxSpeed = maxSpeed;
            agent.maxForce = maxForce;
            Vector2 steering;

            switch (mode) {
                case ARRIVE -> steering = agent.arrive(arriveTarget, slowingRadius);
                case ORBIT -> {
                    Vector2 radial = agent.position.subtract(orbitCenter);
                    double radius = radial.magnitude();
                    Vector2 tangent = new Vector2(-radial.y, radial.x).normalized();
                    Vector2 correction = radial.normalized().multiply((orbitRadius - radius) * 2.0);
                    Vector2 desiredVelocity = tangent.multiply(Math.min(maxSpeed * 0.55, 6.0)).add(correction);
                    steering = desiredVelocity.subtract(agent.velocity);
                }
                case WANDER -> {
                    wanderAngle += (random.nextDouble() * 2.0 - 1.0) * 0.75 * delta;
                    updateWanderTargets(8, wanderRadius);
                    steering = agent.seek(wanderTarget);
                }
                case PATH -> {
                    Vector2 waypoint = route.get(waypointIndex);
                    if (waypointIndex < route.size() - 1
                            && agent.position.subtract(waypoint).magnitude() < 4.5) {
                        waypointIndex++;
                        waypoint = route.get(waypointIndex);
                    }
                    steering = waypointIndex == route.size() - 1
                            ? agent.arrive(waypoint, slowingRadius)
                            : agent.seek(waypoint);
                }
                default -> steering = new Vector2(0, 0);
            }

            agent.velocity = agent.velocity.add(steering.limited(maxForce).multiply(delta)).limited(maxSpeed);
            agent.position = agent.position.add(agent.velocity.multiply(delta));
            if (mode == Mode.WANDER) {
                agent.position = new Vector2(wrap(agent.position.x), wrap(agent.position.y));
            }
            frame++;
            if (frame % 3 == 0) {
                trail.add(agent.position);
                if (trail.size() > 1600) {
                    trail.remove(0);
                }
            }
        }

        private void updateWanderTargets(double circleDistance, double circleRadius) {
            Vector2 heading = agent == null || agent.velocity.magnitude() == 0
                    ? new Vector2(1, 0) : agent.velocity.normalized();
            wanderCenter = agent == null ? new Vector2(50, 50)
                    : agent.position.add(heading.multiply(circleDistance));
            wanderTarget = wanderCenter.add(new Vector2(
                    Math.cos(wanderAngle) * circleRadius,
                    Math.sin(wanderAngle) * circleRadius));
        }

        private double wrap(double value) {
            return (value % 100 + 100) % 100;
        }
    }

    private static class SimulationCanvas extends JPanel {
        private final Simulation simulation;
        private double slowingRadius = 3;
        private double wanderRadius = 2;
        private double orbitRadius = 6;
        private double maxSpeed = 10;
        private double maxForce = 1;

        SimulationCanvas(Simulation simulation) {
            this.simulation = simulation;
            setBackground(BACKGROUND);
            setPreferredSize(new Dimension(760, 620));
        }

        void setParameters(double maxSpeed, double maxForce, double slowingRadius,
                double wanderRadius, double orbitRadius) {
            this.maxSpeed = maxSpeed;
            this.maxForce = maxForce;
            this.slowingRadius = slowingRadius;
            this.wanderRadius = wanderRadius;
            this.orbitRadius = orbitRadius;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            View view = new View(getWidth(), getHeight());

            drawGrid(g, view);
            drawRoute(g, view);
            drawBehavior(g, view);
            drawTrail(g, view);
            drawAgent(g, view);
            drawReadout(g);
            g.dispose();
        }

        private void drawGrid(Graphics2D g, View view) {
            g.setColor(new Color(255, 255, 255, 13));
            g.setStroke(new BasicStroke(1));
            for (int unit = 0; unit <= 100; unit += 10) {
                int x = view.x(unit);
                int y = view.y(unit);
                g.drawLine(x, view.top, x, view.bottom);
                g.drawLine(view.left, y, view.right, y);
            }
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (int unit = 0; unit <= 100; unit += 20) {
                g.drawString(Integer.toString(unit), view.x(unit) + 3, view.bottom + 15);
                g.drawString(Integer.toString(unit), view.left - 22, view.y(unit) + 4);
            }
            g.setColor(new Color(112, 230, 177, 110));
            g.setStroke(new BasicStroke(1.2f));
            g.drawRect(view.left, view.top, view.right - view.left, view.bottom - view.top);
        }

        private void drawRoute(Graphics2D g, View view) {
            if (simulation.mode != Mode.PATH) {
                return;
            }
            List<Vector2> route = simulation.route;
            Path2D path = new Path2D.Double();
            path.moveTo(view.x(route.get(0).x), view.y(route.get(0).y));
            for (int index = 1; index < route.size(); index++) {
                path.lineTo(view.x(route.get(index).x), view.y(route.get(index).y));
            }
            g.setColor(new Color(248, 197, 92, 85));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1, new float[] {8, 7}, 0));
            g.draw(path);
            for (int index = 0; index < route.size(); index++) {
                Vector2 point = route.get(index);
                g.setColor(index <= simulation.waypointIndex ? GOLD : new Color(248, 197, 92, 120));
                drawCircle(g, view.x(point.x), view.y(point.y), index == route.size() - 1 ? 7 : 5, true);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                g.drawString("W" + (index + 1), view.x(point.x) + 9, view.y(point.y) - 8);
            }
        }

        private void drawBehavior(Graphics2D g, View view) {
            switch (simulation.mode) {
                case ARRIVE -> {
                    int x = view.x(simulation.arriveTarget.x);
                    int y = view.y(simulation.arriveTarget.y);
                    drawCircle(g, x, y, view.radius(slowingRadius), false,
                            new Color(112, 190, 235, 105));
                    drawTarget(g, x, y, BLUE);
                    label(g, "ARRIVE TARGET", x + 12, y - 13, BLUE);
                }
                case ORBIT -> {
                    int x = view.x(simulation.orbitCenter.x);
                    int y = view.y(simulation.orbitCenter.y);
                    drawCircle(g, x, y, view.radius(orbitRadius), false,
                            new Color(255, 137, 108, 160));
                    drawCircle(g, x, y, 4, true, CORAL);
                    label(g, "ORBIT CENTER", x + 12, y - 13, CORAL);
                }
                case WANDER -> {
                    int cx = view.x(simulation.wanderCenter.x);
                    int cy = view.y(simulation.wanderCenter.y);
                    int tx = view.x(simulation.wanderTarget.x);
                    int ty = view.y(simulation.wanderTarget.y);
                    g.setColor(new Color(112, 190, 235, 110));
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawLine(view.x(simulation.agent.position.x),
                            view.y(simulation.agent.position.y), cx, cy);
                    drawCircle(g, cx, cy, view.radius(wanderRadius), false,
                            new Color(112, 190, 235, 190));
                    drawCircle(g, tx, ty, 4, true, BLUE);
                    label(g, "WANDER CIRCLE", cx + 12, cy - view.radius(wanderRadius) - 9, BLUE);
                }
                case PATH -> {
                    Vector2 finalPoint = simulation.route.get(simulation.route.size() - 1);
                    drawCircle(g, view.x(finalPoint.x), view.y(finalPoint.y),
                            view.radius(slowingRadius), false, new Color(248, 197, 92, 70));
                    label(g, "ARRIVE / BRAKE", view.x(finalPoint.x) + 12,
                            view.y(finalPoint.y) - 18, GOLD);
                }
            }
        }

        private void drawTrail(Graphics2D g, View view) {
            List<Vector2> trail = simulation.trail;
            if (trail.size() < 2) {
                return;
            }
            Path2D path = new Path2D.Double();
            path.moveTo(view.x(trail.get(0).x), view.y(trail.get(0).y));
            for (int index = 1; index < trail.size(); index++) {
                path.lineTo(view.x(trail.get(index).x), view.y(trail.get(index).y));
            }
            Color trailColor = switch (simulation.mode) {
                case ARRIVE -> new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 130);
                case ORBIT -> new Color(CORAL.getRed(), CORAL.getGreen(), CORAL.getBlue(), 150);
                case WANDER -> new Color(MINT.getRed(), MINT.getGreen(), MINT.getBlue(), 120);
                case PATH -> new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 145);
            };
            g.setColor(trailColor);
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path);
        }

        private void drawAgent(Graphics2D g, View view) {
            Vector2 position = simulation.agent.position;
            Vector2 velocity = simulation.agent.velocity;
            double angle = velocity.magnitude() == 0 ? 0 : Math.atan2(velocity.y, velocity.x);
            int x = view.x(position.x);
            int y = view.y(position.y);
            int size = 13;
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(size, 0);
            arrow.lineTo(-size * 0.75, -size * 0.65);
            arrow.lineTo(-size * 0.48, 0);
            arrow.lineTo(-size * 0.75, size * 0.65);
            arrow.closePath();
            Graphics2D agentGraphics = (Graphics2D) g.create();
            agentGraphics.translate(x, y);
            agentGraphics.rotate(-angle);
            agentGraphics.setColor(new Color(MINT.getRed(), MINT.getGreen(), MINT.getBlue(), 45));
            agentGraphics.fill(new Ellipse2D.Double(-17, -17, 34, 34));
            agentGraphics.setColor(MINT);
            agentGraphics.fill(arrow);
            agentGraphics.setColor(new Color(18, 26, 25));
            agentGraphics.setStroke(new BasicStroke(1.2f));
            agentGraphics.draw(arrow);
            agentGraphics.dispose();
            label(g, "AGENT", x + 15, y + 19, MINT);
        }

        private void drawReadout(Graphics2D g) {
            g.setColor(new Color(PANEL.getRed(), PANEL.getGreen(), PANEL.getBlue(), 225));
            g.fillRoundRect(18, 18, 235, 91, 10, 10);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(simulation.mode.label.toUpperCase(Locale.ROOT), 32, 41);
            g.setColor(MUTED);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            g.drawString(String.format(Locale.ROOT, "position  %5.1f  %5.1f",
                    simulation.agent.position.x, simulation.agent.position.y), 32, 63);
            g.drawString(String.format(Locale.ROOT, "speed     %5.2f / %.1f",
                    simulation.agent.velocity.magnitude(), maxSpeed), 32, 81);
            g.drawString(String.format(Locale.ROOT, "force     %.1f     frame %d", maxForce, simulation.frame),
                    32, 99);
        }

        private void label(Graphics2D g, String text, int x, int y, Color color) {
            g.setColor(color);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            g.drawString(text, x, y);
        }

        private void drawTarget(Graphics2D g, int x, int y, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(2));
            g.drawLine(x - 8, y, x + 8, y);
            g.drawLine(x, y - 8, x, y + 8);
            drawCircle(g, x, y, 4, false, color);
        }

        private void drawCircle(Graphics2D g, int x, int y, int radius, boolean filled) {
            drawCircle(g, x, y, radius, filled, MINT);
        }

        private void drawCircle(Graphics2D g, int x, int y, int radius, boolean filled, Color color) {
            g.setColor(color);
            if (filled) {
                g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            } else {
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
            }
        }
    }

    private static class View {
        final int left;
        final int top;
        final int right;
        final int bottom;
        final double scale;

        View(int width, int height) {
            left = 48;
            top = 45;
            right = Math.max(left + 1, width - 28);
            bottom = Math.max(top + 1, height - 45);
            scale = Math.min((right - left) / 100.0, (bottom - top) / 100.0);
        }

        int x(double worldX) {
            return left + (int) Math.round(worldX * scale);
        }

        int y(double worldY) {
            return bottom - (int) Math.round(worldY * scale);
        }

        int radius(double worldRadius) {
            return (int) Math.round(worldRadius * scale);
        }
    }

    private static class ControlPanel extends JPanel {
        private final Simulation simulation;
        private final JLabel modeDescription = new JLabel();
        private final JSlider speedSlider;
        private final JSlider forceSlider;
        private final JSlider slowingSlider;
        private final JSlider wanderSlider;
        private final JSlider orbitSlider;
        private final Timer timer;
        private boolean paused;

        ControlPanel(Simulation simulation, SimulationCanvas canvas) {
            this.simulation = simulation;
            setBackground(PANEL);
            setPreferredSize(new Dimension(290, 620));
            setBorder(BorderFactory.createEmptyBorder(24, 22, 20, 22));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            JLabel eyebrow = new JLabel("AI IN GAMES  /  LAB 01");
            eyebrow.setForeground(MINT);
            eyebrow.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            add(eyebrow);
            add(Box.createVerticalStrut(9));

            JLabel title = new JLabel("Steering Lab");
            title.setForeground(TEXT);
            title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            add(title);
            add(Box.createVerticalStrut(5));

            JLabel subtitle = new JLabel("Live behavior playground");
            subtitle.setForeground(MUTED);
            subtitle.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            add(subtitle);
            add(Box.createVerticalStrut(25));

            add(sectionLabel("BEHAVIOR"));
            JComboBox<Mode> modeSelector = new JComboBox<>(Mode.values());
            modeSelector.setSelectedItem(Mode.ARRIVE);
            modeSelector.setBackground(PANEL_LIGHT);
            modeSelector.setForeground(TEXT);
            modeSelector.setFocusable(false);
            modeSelector.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            modeSelector.addActionListener(event -> {
                simulation.setMode((Mode) modeSelector.getSelectedItem());
                updateDescription();
                canvas.repaint();
            });
            add(modeSelector);
            add(Box.createVerticalStrut(7));
            modeDescription.setForeground(MUTED);
            modeDescription.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            add(modeDescription);
            updateDescription();

            add(Box.createVerticalStrut(24));
            add(sectionLabel("LIVE PARAMETERS"));
            speedSlider = addSlider("Max speed", 2, 20, 10, 1, " u/s");
            forceSlider = addSlider("Max force", 1, 30, 1, 1, "");
            slowingSlider = addSlider("Slowing radius", 5, 120, 30, 10, " u");
            wanderSlider = addSlider("Wander radius", 5, 80, 20, 10, " u");
            orbitSlider = addSlider("Orbit radius", 20, 140, 60, 10, " u");

            add(Box.createVerticalStrut(18));
            JButton resetButton = makeButton("Reset simulation", false);
            resetButton.addActionListener(event -> {
                simulation.reset();
                canvas.repaint();
            });
            add(resetButton);
            add(Box.createVerticalStrut(8));

            JButton pauseButton = makeButton("Pause", true);
            pauseButton.addActionListener(event -> {
                paused = !paused;
                pauseButton.setText(paused ? "Resume" : "Pause");
            });
            add(pauseButton);
            add(Box.createVerticalGlue());

            JLabel note = new JLabel("Drag sliders while the agent moves.");
            note.setForeground(MUTED);
            note.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
            add(note);

            timer = new Timer(16, event -> {
                if (!paused) {
                    simulation.update(DELTA_TIME, speedSlider.getValue(), forceSlider.getValue(),
                            slowingSlider.getValue() / 10.0, wanderSlider.getValue() / 10.0,
                            orbitSlider.getValue() / 10.0);
                    canvas.setParameters(speedSlider.getValue(), forceSlider.getValue(),
                            slowingSlider.getValue() / 10.0, wanderSlider.getValue() / 10.0,
                            orbitSlider.getValue() / 10.0);
                    canvas.repaint();
                }
            });
            timer.start();
        }

        private JLabel sectionLabel(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(MUTED);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            label.setAlignmentX(LEFT_ALIGNMENT);
            return label;
        }

        private JSlider addSlider(String title, int minimum, int maximum, int initial,
                double divisor, String suffix) {
            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

            JPanel labelRow = new JPanel(new BorderLayout());
            labelRow.setOpaque(false);
            JLabel label = new JLabel(title);
            label.setForeground(TEXT);
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            JLabel value = new JLabel();
            value.setForeground(MINT);
            value.setHorizontalAlignment(SwingConstants.RIGHT);
            value.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
            labelRow.add(label, BorderLayout.WEST);
            labelRow.add(value, BorderLayout.EAST);

            JSlider slider = new JSlider(minimum, maximum, initial);
            slider.setOpaque(false);
            slider.setFocusable(false);
            slider.setPaintTicks(true);
            slider.setMajorTickSpacing((maximum - minimum) / 3);
            slider.setMinorTickSpacing(0);
            slider.setForeground(MINT);
            slider.addChangeListener(event -> value.setText(formatValue(slider.getValue(), divisor) + suffix));
            value.setText(formatValue(initial, divisor) + suffix);

            row.add(labelRow);
            row.add(slider);
            add(row);
            add(Box.createVerticalStrut(4));
            return slider;
        }

        private String formatValue(int value, double divisor) {
            return divisor == 1 ? Integer.toString(value)
                    : String.format(Locale.ROOT, "%.1f", value / divisor);
        }

        private JButton makeButton(String text, boolean secondary) {
            JButton button = new JButton(text);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            button.setFocusPainted(false);
            button.setForeground(secondary ? TEXT : BACKGROUND);
            button.setBackground(secondary ? PANEL_LIGHT : MINT);
            button.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            return button;
        }

        private void updateDescription() {
            String text = switch (simulation.mode) {
                case ARRIVE -> "Brake smoothly at the target.";
                case ORBIT -> "Hold a stable radius around the center.";
                case WANDER -> "Patrol freely using the circle gizmo.";
                case PATH -> "Follow five waypoints, then arrive.";
            };
            modeDescription.setText("<html>" + text + "</html>");
        }
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | javax.swing.UnsupportedLookAndFeelException ignored) {
                // Keep Swing's default look and feel if the platform theme is unavailable.
            }

            Simulation simulation = new Simulation();
            SimulationCanvas canvas = new SimulationCanvas(simulation);
            ControlPanel controls = new ControlPanel(simulation, canvas);
            JFrame frame = new JFrame("Steering Lab | AI in Games");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(970, 650));
            frame.setLayout(new BorderLayout());
            frame.add(controls, BorderLayout.WEST);
            frame.add(canvas, BorderLayout.CENTER);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}