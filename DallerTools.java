import EDU.gatech.cc.is.util.Vec2;
import EDU.gatech.cc.is.abstractrobot.*;
import java.lang.Boolean;
import java.util.*;

public class DallerTools{

    /**
     * <code>PETIT_ANGLE</code> = _PI_ / 20
     */
    public static final double PETIT_ANGLE = Math.PI/20;

    /**
     * @return _vrai_ si les deux directions sont à peu près alignées
     * @see PETIT_ANGLE
     */
    public static boolean isBehind( Vec2 point, Vec2 orient)
    {
        return Math.abs( point.t - orient.t) < PETIT_ANGLE;
    }


    /**
     * Position similaire (à _dm_ près)
     * @return _vrai_ si les deux points sont à une distance inférieure à dm 
     */
     public static boolean simPosition(Vec2 p1, Vec2 p2, double dm){
        Vec2 diff = new Vec2(p1);
        diff.sub(p2);
        return p2.r <= dm;
     }

     /**
      * simPosition(p1, p2, 0.1)
      */
     public static boolean simPosition(Vec2 p1, Vec2 p2){
        return simPosition(p1, p2, 0.1);
     }


     /** 
      * Produit scalaire de p1 et p2 donnés en vue subjective
      */
     public static double pscal(Vec2 p1, Vec2 p2){
        double a = p1.t - p2.t;
        return p1.r * p2.r * Math.cos(a);
     }
}
