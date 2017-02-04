package Domain.Navigation;


import java.util.LinkedList;
import java.util.List;

import Domain.Exception.PathFindingException;
import Domain.Map.*;

/**
 * Direction tells you how to get from
 */
public class Guidance extends Path {

    LinkedList<String> textDirections;

    public Guidance (Destination start, Destination end) throws PathFindingException {
            super(start, end);
    }

    public List getTextDirections()
    {
        return textDirections;
    }
}
