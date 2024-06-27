package de.innfactory.smithy4play.openapi;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AnnotationTrait;
import software.amazon.smithy.model.traits.AbstractTrait;

public class Smithy4PlayServiceTrait extends AnnotationTrait {
 
    public static ShapeId ID = ShapeId.from("smithy.smithy4play#smithy4playservice");
 
    public Smithy4PlayServiceTrait(ObjectNode node) {
        super(ID, node);
    }
 
    public Smithy4PlayServiceTrait() {
        super(ID, Node.objectNode());
    }
 
    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }
 
        @Override
        public Smithy4PlayServiceTrait createTrait(ShapeId target, Node node) {
            return new Smithy4PlayServiceTrait(node.expectObjectNode());
        }
    }
}