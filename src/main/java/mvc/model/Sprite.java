package mvc.model;

import mvc.controller.CommandCenter;
import mvc.controller.Game;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import mvc.controller.GameOp;
import mvc.controller.Utils;
import lombok.Data;
import lombok.experimental.Tolerate;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

//the lombok @Data gives us automatic getters and setters on all members

//A Sprite can be either vector or raster. We do not implement the draw(Graphics g) method, thereby forcing extending
// classes to implement draw() depending on their graphics mode: vector or raster.
// draw().
@Data
public abstract class Sprite implements Movable {
    //the center-point of this sprite
    private Point center;
    //this causes movement; change-in-x and change-in-y
    private double deltaX, deltaY;

    //every sprite has a team: friend, foe, floater, or debris.
    private Team team;
    //the radius of circumscribing/inscribing circle
    private int radius;

    //orientation from 0-359
    private int orientation;
    //natural mortality (short-lived sprites only)
    private int expiry;

    //some sprites spin, such as floaters and asteroids
    private int spin;


    //these are Cartesian points used to draw the polygon in vector mode.
    //Once set, their values do not change. It's the job of the renderVector() method to adjust for orientation and
    // location.
    private Point[] cartesians;

    //used for vector rendering
    private Color color;

    //Either you use the cartesian points and color above (vector), or you can use the BufferedImages here (raster).
    //Keys in this map can be any object (?) you want.
    private Map<?, BufferedImage> rasterMap;


    //constructor
    public Sprite() {

        //place the sprite at some random location in the game-space at instantiation
        setCenter(new Point(Game.R.nextInt(Game.DIM.width),
                Game.R.nextInt(Game.DIM.height)));


    }


    @Override
    public void move() {

        //The following code block just keeps the sprite inside the bounds of the frame.
        //To ensure this behavior among all sprites in your game, make sure to call super.move() in extending classes
        // where you need to override the move() method.

        //right-bounds reached
        if (center.x > Game.DIM.width) {
            setCenter(new Point(1, center.y));
        //left-bounds reached
        } else if (center.x < 0) {
            setCenter(new Point(Game.DIM.width - 1, center.y));
        //bottom-bounds reached
        } else if (center.y > Game.DIM.height) {
            setCenter(new Point(center.x, 1));
        //top-bounds reached
        } else if (center.y < 0) {
            setCenter(new Point(center.x, Game.DIM.height - 1));
        //in-bounds
        } else {
            double newXPos = center.x + getDeltaX();
            double newYPos = center.y + getDeltaY();
            setCenter(new Point((int) newXPos, (int) newYPos));
        }

        //expire (decrement expiry) on short-lived objects only
        //the default value of expiry is zero, so this block will only apply to expiring sprites
        if (expiry > 0) expire();

        //if a sprite spins, adjust its orientation
        //the default value of spin is zero, therefore non-spinning objects will not call this block.
        if (spin != 0) orientation += spin;


    }

    void expire() {

        //if a short-lived sprite has an expiry of one, it commits suicide by enqueuing itself (this) onto the
        //opsList with an operation of REMOVE
        if (expiry == 1) {
            CommandCenter.getInstance().getOpsQueue().enqueue(this, GameOp.Action.REMOVE);
        }
        //and then decrements in all cases
        expiry--;

    }


    //utility method used by extending (thus protected keyword) classes to produce random pos/neg values
    protected int somePosNegValue(int seed) {
        int randomNumber = Game.R.nextInt(seed);
        return (randomNumber % 2 == 0) ? randomNumber : -randomNumber;
    }

    protected int somePosValue(int seed) {
        return Game.R.nextInt(seed);
    }



    //used to load raster graphics
    protected BufferedImage loadGraphic(String imagePath) {
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(Objects.requireNonNull(Sprite.class.getResourceAsStream(imagePath)));
        }
        catch (IOException e) {
            e.printStackTrace();
            bufferedImage = null;
        }
        return bufferedImage;
    }



    //https://www.tabnine.com/code/java/methods/java.awt.geom.AffineTransform/rotate
    protected void renderRaster(Graphics2D g2d, BufferedImage bufferedImage) {

        if (bufferedImage ==  null) return;

        int centerX = getCenter().x;
        int centerY = getCenter().y;
        int width = getRadius() * 2;
        int height = getRadius() * 2;
        double angleRadians = Math.toRadians(getOrientation());

        AffineTransform oldTransform = g2d.getTransform();
        try {
            double scaleX = width * 1.0 / bufferedImage.getWidth();
            double scaleY = height * 1.0 / bufferedImage.getHeight();

            AffineTransform affineTransform = new AffineTransform( oldTransform );
            if ( centerX != 0 || centerY != 0 ) {
                affineTransform.translate( centerX, centerY );
            }
            affineTransform.scale( scaleX, scaleY );
            if ( angleRadians != 0 ) {
                affineTransform.rotate( angleRadians );
            }
            affineTransform.translate( -bufferedImage.getWidth() / 2.0, -bufferedImage.getHeight() / 2.0 );

            g2d.setTransform( affineTransform );

            g2d.drawImage( bufferedImage, 0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null );
        } finally {
            g2d.setTransform( oldTransform );

        }
    }

    protected void renderVector(Graphics g) {

        //set the graphics context color to the color of the sprite
        g.setColor(color);

        // To render this Sprite in vector mode, we need to, 1: convert raw cartesians to raw polars, 2: rotate polars
        // for orientation of sprite. 3: Convert back to cartesians 4: adjust for center-point (location).
        // and 5: pass the cartesian-x and cartesian-y coords as arrays, along with length, to g.drawPolygon().

        //1: convert raw cartesians to raw polars (used later in stream below).
        //The reason we convert cartesian-points to polar-points is that it's much easier to rotate polar-points
        List<PolarPoint> polars = Utils.cartesianToPolar(getCartesians());

        //2: rotate raw polars given the orientation of the sprite.
        Function<PolarPoint, PolarPoint> rotatePolarByOrientation =
                pp -> new PolarPoint(
                        pp.getR(),
                        pp.getTheta() + Math.toRadians(getOrientation()) //rotated Theta
                );

        //3: convert the rotated polars back to cartesians
        Function<PolarPoint, Point> polarToCartesian =
                pp -> new Point(
                        (int)  (pp.getR() * getRadius() * Math.sin(pp.getTheta())),
                        (int)  (pp.getR() * getRadius() * Math.cos(pp.getTheta())));

        //4: adjust the cartesians for the location (center-point) of the sprite.
        // the reason we subtract the y-value has to do with how Java plots the vertical axis for
        // graphics (from top to bottom)
        Function<Point, Point> adjustForLocation =
                p -> new Point(
                         getCenter().x + p.x,
                         getCenter().y - p.y);




        //5: draw the polygon using the List of raw polars from above, applying mapping transforms as required
        g.drawPolygon(
                polars.stream()
                        .map(rotatePolarByOrientation)
                        .map(polarToCartesian)
                        .map(adjustForLocation)
                        .map(pnt -> pnt.x)
                        .mapToInt(Integer::intValue)
                        .toArray(),

                polars.stream()
                        .map(rotatePolarByOrientation)
                        .map(polarToCartesian)
                        .map(adjustForLocation)
                        .map(pnt -> pnt.y)
                        .mapToInt(Integer::intValue)
                        .toArray(),

                polars.size());

        //for debugging center-point and collision. Feel free to remove these three lines.
        //#########################################
        //g.setColor(Color.GRAY);
        //g.fillOval(getCenter().x - 1, getCenter().y - 1, 2, 2);
        //g.drawOval(getCenter().x - getRadius(), getCenter().y - getRadius(), getRadius() *2, getRadius() *2);
        //#########################################
    }

    @Override
    public void dead() {

    }

    //default behavior for adding and removing objects from game space
    @Override
    public void add(LinkedList<Movable> list) {
        list.add(this);
    }

    @Override
    public void remove(LinkedList<Movable> list) {
        list.remove(this);
    }

}
