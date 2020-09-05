import	EDU.gatech.cc.is.util.Vec2;
import	EDU.gatech.cc.is.abstractrobot.*;
import java.lang.Boolean;
import java.util.*;

/**
 * # Équipe DALLER
 *
 * ### Stratégie :
 * On a un gardien de but qui se déplace dans son but en suivant la balle.
 * Pour les autres joueurs, chacun déduit son rôle (comportement) en fonction de
 * perceptions de l'environnement.
 *
 * <img src="doc/TeamDaller.png" />
 *
 * * __Défendre :__ Comportement de base, quand l'autre équipe a la balle 
 * * __Se démarquer :__ Si on défend et qu'on est le plus près du but adverse 
 * * __Suivre :__ Quand quelqu'un de notre équipe a la balle
 * * __Attaquer :__ Quand c'est moi qui ai la balle ou quand je défend et que la balle s'approche
 *
 * Le comportement _Se démarquer_ implique un blocage dans cette situation, pour
 * qu'un joueur de l'équipe puisse faire la passe. Ainsi après avoir été 
 * démarqué, si notre équipe reprend la balle, je reste démarqué pour qu'on 
 * puisse me faire une passe. Je peux me débloquer si :
 *
 * * Je ne suis plus le plus avancé sur le terrain
 * * La balle me dépasse
 *
 * Exemples de résultats :
 * * __Daller__ vs __BasicTeam__ : 15 - 1
 * * __Daller__ vs __BrianTeam__ : 5 - 0
 * * __Daller__ vs __DTeam__ : 1 - 0
 * * __BasicTeam__ vs __Daller__ : 0 - 20
 * * __BrianTeam__ vs __Daller__ : 0 - 3
 * * __DTeam__ vs __Daller__ : 5 - 1
 *
 * @author Évariste DALLER
 */
public class Daller extends ControlSystemSS{


    /**
     * Qui a la balle : qui est le plus proche
     * * Moi (ME)
     * * Un autre joueur de mon équipe (US)
     * * Un joueur adverse (THEM)
     */
    enum HasBall{
        ME, US, THEM
    }
    
    /**
     * Distance à partir de laquelle on est considéré
     * près de quelque chose
     */
    public static final double DIST_CLOSE = 0.5;

    /*********************
     *  vision du monde  *
     *********************/

    // players
    private Vec2 me;
    private Vec2[] teammates;
    private Vec2[] opponents;

    // goal
    private Vec2 myGoal;
    private Vec2 theirGoal;

    // others
    private Vec2 ball;
    private Vec2 center;

    private int side;	//côté du terrain
    private int id;	//id du joueur

    // Comportements
    private DallerBehavior[] behaviors;
    private HashMap<String, Boolean> perceptions;
    
    
    public HashMap<String, Boolean> perceptions(){
        return perceptions;
    }


    /**
     * Hérité de ControlSystemSS.
     * Action du joueur à chaque étape.
     */
    public int takeStep(){
        update();
        
        // Initialisation 
        Vec2 dir = new Vec2(0,0);
        abstract_robot.setSteerHeading(-1, dir.t);
        abstract_robot.setSpeed(-1, 1.0);

        if (id == 0){
            goalie();
        }
        else{
            // On teste les comportements
            for (int i=0; i<behaviors.length; i++){
                behaviors[i].test();
            }

            if (behaviors[0].active()){
                defend();
            }else if (behaviors[1].active()){
                demarque();
            }else if (behaviors[2].active()){
                follow();
            }else if (behaviors[3].active()){
                attack();
            }
        }

        return (CSSTAT_OK);
    }


    /**
     * Met à jour la perception de l'environnement
     */
    private void update(){
        id = abstract_robot.getPlayerNumber(-1);
        me = abstract_robot.getPosition(-1);
        center = new Vec2(-me.x,-me.y);
        ball = abstract_robot.getBall(-1);
        myGoal = abstract_robot.getOurGoal(-1);
        theirGoal = abstract_robot.getOpponentsGoal(-1);
        teammates= abstract_robot.getTeammates(-1);
        opponents = abstract_robot.getOpponents(-1);

        //Connaître la direction de son but pour se placer
        if(myGoal.x < 0) side = 1;
        else side = -1;
        
        updatePerceptions();
    }


    /**
     * Met à jour les perceptions à l'origine des comportements.
     *
     * Les perceptions sont des booléens, décrivant une réalité et
     * déclanchant les comportements. Elles sont :
     * * WeHaveBall : L'équipe a la balle (un joueur en est le plus proche)
     * * IHaveBall : J'ai la balle (je suis le plus proche)
     * * CloseToBall : Je suis plus près d'une distance seuil de la balle
     * * CloseToTheirGoal : Je suis le plus près du but adverse (dans mon équipe)
     *
     * @see DallerBehavior
     * @see DIST_CLOSE
     */
    private void updatePerceptions(){
        // Qui est le plus proche de la balle 
        HasBall who = whoHasBall();
        switch(who){
            case ME:
                perceptions.put("IHaveBall", new Boolean(true));
                perceptions.put("WeHaveBall", new Boolean(true));
                break;
            case US:
                perceptions.put("IHaveBall", new Boolean(false));
                perceptions.put("WeHaveBall", new Boolean(true));
                break;
            case THEM:
                perceptions.put("IHaveBall", new Boolean(false));
                perceptions.put("WeHaveBall", new Boolean(false));
        }
        
        // Suis-je le plus proche de leur but ?
        double dmin = theirGoal.r;
        boolean close = true;
        for (Vec2 tm : teammates){
            Vec2 t = new Vec2(tm);
            t.sub(theirGoal);
            double d = t.r;
            if (d < dmin){
                dmin = d;
                close = false;
            }
        }
        perceptions.put("CloserToTheirGoal", new Boolean(close));

        // Suis-je près de la balle
        if (ball.r <= DIST_CLOSE) close = true;
        else close = false;
        perceptions.put("CloseToBall", new Boolean(close));
        
        
        // Dois-je toujours être démarqué
        if (perceptions.get("Demarque") && !(perceptions.get("CloserToTheirGoal"))){
            perceptions.put("Demarque", new Boolean(false));
        }
    }


    /** 
     * Retourne le code correspondant à celui qui est le plus proche
     * de la balle
     *
     * @see HasBall
     */
    private HasBall whoHasBall(){
        HasBall who = HasBall.ME;
        double dmin = ball.r;
        
        // Comparaison avec les copains
        for (Vec2 tm : teammates){
            Vec2 t = new Vec2(tm);
            t.sub(ball);
            double d = t.r;
            if (d < dmin){
                dmin = d;
                who = HasBall.US;
            }
        }
        
        // Comparaison avec les méchants
        for (Vec2 op : opponents){
            Vec2 t = new Vec2(op);
            t.sub(ball);
            double d = t.r;
            if (d < dmin){
                dmin = d;
                who = HasBall.THEM;
            }
        }
        
        return who;
    }


    /**
     * Gardien de but : reste devant le but et suit la balle.
     */
    private void goalie(){
        abstract_robot.setDisplayString("gardien");

        // Dans les buts et rester en face de la balle
        Vec2 pos = new Vec2(myGoal.x + 0.04 * side, ball.y);

        //déplacement
        abstract_robot.setSteerHeading(-1,pos.t);

        //vitesse
        double speed = 1.0;

        //ne pas dépasser des buts
        if((pos.y < 0 && me.y < -0.25) || (pos.y > 0 && me.y > 0.25))
            speed = 0;

        abstract_robot.setSpeed(-1, speed);
    }

    /**
     * Défenseur :
     * * Va en position défensive si il ne l'est pas déja
     * * Suit la balle et attaque si elle est assez proche
     *
     * Les positions défensives sont tous les points du demi-cercle
     * de centre _myGoal_ et de rayon <code>DIST_CLOSE * 1.5</code>
     *
     * Pour répartir les joueurs en défense, on utilise un champ de 
     * répulsion entre joueurs, dont le coefficient de répulsion est
     * proportionel au carré de la distance entre les joueurs 
     * (inspiré de l'attraction gravitationnelle).
     */
    private void defend(){
        if (perceptions.get("CloseToBall")){
            attack();
        }
        else{
            abstract_robot.setDisplayString("défendre");
            
            // Si on n'est pas encore en position : on y va
            if (myGoal.r > DIST_CLOSE){
            
                Vec2 dir = new Vec2(myGoal);
                for (Vec2 tm : teammates){
                    // Si ce joueur est trop près de moi, je m'éloigne de manière
                    // inversement proportionelle au carré de la distance entre nous
                    if (tm.r < 0.3){
                        Vec2 dif = new Vec2(tm);
                        dif.normalize((1.0 / (tm.r * tm.r)) * 0.02);
                        dir.sub(dif);
                    }
                }
            
                abstract_robot.setSteerHeading(-1, dir.t);
                abstract_robot.setSpeed(-1, 1.0);
            }
            // Sinon, on fixe la balle
            else {
                abstract_robot.setSteerHeading(-1, ball.t);
                abstract_robot.setSpeed(-1, 0.0);
            }
        }
    }


    /**
     * Aller en position d'attaquant et y rester, en fixant la balle.
     *
     * La position d'attaque est <code>theirGoal + (-1.0 * SIDE ; 0.6) </code> <br />
     * <img src="doc/demarque.png" />
     */
    private void demarque(){
        abstract_robot.setDisplayString("se démarquer");
        if (!(perceptions.get("Demarque"))) {
            perceptions.put("Demarque", new Boolean(true));
        }

        // Calcul de la position d'attaque
        Vec2 pos = new Vec2(theirGoal);
        Vec2 dif = new Vec2(-1.0 * side, 0.6);
        pos.add(dif);
           
        // Si on n'est pas en gros sur la position : on y va
        if (pos.r > 0.1){
            abstract_robot.setSteerHeading(-1, pos.t);
            abstract_robot.setSpeed(-1, 1.0);
        }
        // Sinon, on y reste et on regarde la baballe
        else {
            abstract_robot.setSteerHeading(-1, ball.t);
            abstract_robot.setSpeed(-1, 0.0);
        }
        
        
        // Si la balle est en direction du but, on y va !
        if (DallerTools.isBehind(ball, theirGoal)){
            perceptions.put("Demarque", new Boolean(false));
            attack();
        }
    }

    /**
     * Suivre (pour les joueurs n'ayant pas la balle)
     */
    private void follow(){
        // Bloquage démarqué si on l'est déjà 
        if (perceptions.get("Demarque")) demarque();
        else{
            abstract_robot.setDisplayString("suivre");
            abstract_robot.setSteerHeading(-1, ball.t);
            abstract_robot.setSpeed(-1, 1.0);
        }
    }

    /**
     * Attaquer : joueur ayant la balle.
     * * Se dirige vers le but adverse
     * * Tire si il est à portée
     * * Passe à une personne démarquée si il y en a une 
     *
     * @see joueurDemarque
     */
    private void attack(){
        if (perceptions.get("Demarque")) demarque();
        else{
            abstract_robot.setDisplayString("attaquer");
            
            Vec2 target = new Vec2(theirGoal);
            
            // Si un joueur jd est démarqué
            // La cible devient un point légèrement en avant de lui
            Vec2 jd = joueurDemarque();
            if (jd != me){
                target.sub(jd);
                target.normalize(0.2);
                target.add(jd);    // Ici target est un peu en avant de jd
            }
            
            target = theirGoal;
            
            Vec2 dir = new Vec2(ball);
            dir.sub(target);
            dir.normalize(0.05);
            dir.add(ball);
           
            
            // si on est à portée on tire
            if (target.r < DIST_CLOSE){
                kick();
            }
            // Sinon on avance
            else{
                abstract_robot.setSteerHeading(-1, dir.t);
                abstract_robot.setSpeed(-1, 1.0);
            }
        }
    }



    /**
     * Joueur démarqué de mon équipe le plus proche
     * Un joueur est considéré démarqué (en dehors de son statut interne) si
     * il est :
     * * Plus proche du but adverse que moi
     * * Accessible par une passe, c-à-d il n'y a personne entre lui et moi selon un couloir
     *
     * Pour déterminer si un joueur est dans le couloir, on utilise :
     * * Le produit scalaire pour vérifier si ce joueur n'est pas derrière nous
     * * Le projeté orthogonal de ce joueur pour vérifier si il est dans le couloir
     *
     * <img src="../jdemarque.png" />
     * 
     * @return 
     * * la position du joueur de mon équipe démarqué le plus proche de moi s'il y en a un
     * * me sinon
     */
    private Vec2 joueurDemarque(){

        Vec2 jd = me;
        double dist = 999.9;
        double lCouloir = 0.2;
        for (Vec2 tm: teammates){
        
            // Si ce joueur est plus près du but
            Vec2 dif = new Vec2(tm);
            dif.sub(theirGoal);
            if (dif.r < theirGoal.r && tm.r > 0.5){
                
                // On teste si il est démarqué
                // i.e. aucun joueur enemi n'est dans le couloir
                boolean dem = true;
                for (Vec2 op: opponents){
                    // Verif du projeté ortho
                    double dort = op.r * Math.cos(tm.t - op.t);
                    if (DallerTools.pscal(tm, op) > 0 && dort < lCouloir){
                        dem = false;
                        break;
                    }
                }
                
                if (dem) {
                    if (dist > tm.r){
                       jd = tm;
                       dist = tm.r;
                    }
                }
            }
        }
    
        return jd;
    }


    /**
     * Éviter de rentrer dans les autres joueurs
     * @return Un vecteur somme de l'opposé de chacun des coéquipiers, pondérés
     * par l'inverse du carré de la distance nous séparant.
     */
    private Vec2 avoidCollision(){
        Vec2 ac = new Vec2();
        for (Vec2 tm : opponents){
            if (tm.r < 0.2){
                Vec2 tmp = new Vec2(tm);
                tmp.normalize((1.0 / tm.r) * 0.002);
                ac.add(tmp);
            }
        }
        return ac;
    }
    
    
    /**
     * Tire
     */
    private void kick(){
        if(abstract_robot.canKick(-1)){
            abstract_robot.kick(-1);
        }
    }
    
    

    /**
     * Initialisation du robot
     */
    public void configure(){
        perceptions = new HashMap<String, Boolean>();
        perceptions.put("WeHaveBall", new Boolean(false));
        perceptions.put("IHaveBall", new Boolean(false));
        perceptions.put("CloserToTheirGoal", new Boolean(false));
        perceptions.put("Demarque", new Boolean(false));
        perceptions.put("CloseToBall", new Boolean(false));

        DallerBehavior def = new DallerBehavior(this, new String[0], new DallerBehavior[0]);

        String[] names = {"CloserToTheirGoal"};
        DallerBehavior[] bev = {def};
        DallerBehavior dem = new DallerBehavior(this, names, bev);

        String[] names1 = {"WeHaveBall"};
        DallerBehavior[] bev1 = {def, dem};
        DallerBehavior fol = new DallerBehavior(this, names1, bev1);

        String[] names2 = {"WeHaveBall", "IHaveBall"};
        DallerBehavior[] bev2 = {def, dem, fol};
        DallerBehavior att = new DallerBehavior(this, names2, bev2);

        behaviors = new DallerBehavior[4];
        behaviors[0] = def;
        behaviors[1] = dem;
        behaviors[2] = fol;
        behaviors[3] = att;
    }
}

