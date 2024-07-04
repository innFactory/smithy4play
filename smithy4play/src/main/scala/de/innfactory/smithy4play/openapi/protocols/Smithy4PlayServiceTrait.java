package de.innfactory.smithy4play.openapi.protocols;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.AnnotationTrait;

public class Smithy4PlayServiceTrait extends AnnotationTrait {

    public static ShapeId ID = ShapeId.from("de.innfactory.smithy4play.openapi.protocols#smithy4PlayService");

    public Smithy4PlayServiceTrait(ObjectNode node) {
        super(ID, node);
        System.out.println(this.getClass().toString());
        System.out.println("Shape ID de.innfactory.smithy4play.openapi.protocols#smithy4PlayService");
    }
 
    public Smithy4PlayServiceTrait() {
        super(ID, Node.objectNode());
        System.out.println("Shape ID de.innfactory.smithy4play.openapi.protocols#smithy4PlayService");
    }
 
    public static final class Provider extends AbstractTrait.Provider {

        public Provider() {
            super(ID);
            System.out.println("Smithy4PlayServiceTrait Provider");
        }
 
        @Override
        public Smithy4PlayServiceTrait createTrait(ShapeId target, Node node) {
            System.out.println("Smithy4PlayServiceTrait Provider createTrait");
            System.out.println(target.toString());


            return new Smithy4PlayServiceTrait(node.expectObjectNode());
        }
    }
}