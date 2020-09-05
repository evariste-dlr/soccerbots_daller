import java.util.*;


/**
 * Classe gérant les comportements
 *
 * @author Évariste DALLER
 */
public class DallerBehavior {

    protected boolean active;           /**< Vrai si le comportement doit être lancé */
    protected String[] pNames;    /**< Noms des perceptions à prendre en compte */
    protected Daller parent;     /**< Robot parent */
    protected DallerBehavior[] cInhib;    /**< Liste des comportements à inhiber */


    /**
     * Constructeur
     */
    public DallerBehavior(Daller p,    /**< Le robot parent */
                          String[] pn,      /**< Noms des perceptions */
                          DallerBehavior[] i /**< Comportements à inhiber */
                         ){
        active = false;
        parent = p;
        pNames = (String[])pn.clone();
        cInhib = (DallerBehavior[])i.clone();
    }


     /**
      * Désactiver
      */
     public void deactivate(){
         active = false;
     }

     /**
      * Accesseur à active
      */
     public boolean active(){
         return this.active;
     }


    /**
     * Teste si le comportement doit être actif, en fonction
     * des perceptions à prendre en compte.
     * Si le comportement doit être lancé, this.actif sera vrai
     */
    public void test(){
        active = true;
        for (String name : pNames){
            if (parent.perceptions().containsKey(name)){
                active = active && parent.perceptions().get(name);
            }
        }

        if (active){
            inhib();
        }
    }

    /**
     * Désactiver les comportements que l'on doit inhiber
     */
    public void inhib(){
        for (DallerBehavior cmp : cInhib){
            cmp.deactivate();
        }
    }

}
