package de.innfactory.smithy4play.openapi;

import de.innfactory.smithy4play.openapi.protocols.Smithy4PlayServiceTrait;

public class Smithy4PlayOpenApiProtocol extends Smithy4PlayOpenApiProtocolAbstr {
    public Class<Smithy4PlayServiceTrait> getProtocolType() {
        return Smithy4PlayServiceTrait.class;
    }
}
