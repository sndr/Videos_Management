import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.geom.Rectangle2D;

/**
 * Ícone vetorial desenhado via Java2D com as cores oficiais do Google.
 * Não requer arquivos de imagem externos e suporta qualquer resolução.
 */
public class GoogleIcon implements Icon {
    private final int size;

    public GoogleIcon(int size) {
        this.size = size;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double s = size;
        double cx = x + s / 2.0;
        double cy = y + s / 2.0;
        double rOuter = s * 0.44;
        double thickness = s * 0.22;
        double rCenter = rOuter - thickness / 2.0;

        g2.setStroke(new BasicStroke((float) thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        // Vermelho Google (topo)
        g2.setColor(new Color(234, 67, 53));
        g2.draw(new Arc2D.Double(cx - rCenter, cy - rCenter, rCenter * 2, rCenter * 2, 45, 90, Arc2D.OPEN));

        // Amarelo Google (esquerda)
        g2.setColor(new Color(251, 188, 5));
        g2.draw(new Arc2D.Double(cx - rCenter, cy - rCenter, rCenter * 2, rCenter * 2, 135, 90, Arc2D.OPEN));

        // Verde Google (base)
        g2.setColor(new Color(52, 168, 83));
        g2.draw(new Arc2D.Double(cx - rCenter, cy - rCenter, rCenter * 2, rCenter * 2, 225, 90, Arc2D.OPEN));

        // Azul Google (curva inferior direita)
        g2.setColor(new Color(66, 133, 244));
        g2.draw(new Arc2D.Double(cx - rCenter, cy - rCenter, rCenter * 2, rCenter * 2, 315, 60, Arc2D.OPEN));

        // Barra horizontal Azul Google (do centro até o limite direito)
        double barHeight = thickness;
        double barWidth = rOuter;
        g2.fill(new Rectangle2D.Double(cx - 0.5, cy - barHeight / 2.0, barWidth + 0.5, barHeight));

        g2.dispose();
    }
}
