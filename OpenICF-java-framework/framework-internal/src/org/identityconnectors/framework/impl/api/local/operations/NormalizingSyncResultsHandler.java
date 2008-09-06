package org.identityconnectors.framework.impl.api.local.operations;

import org.identityconnectors.common.Assertions;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;

public class NormalizingSyncResultsHandler implements SyncResultsHandler {
    
    private final SyncResultsHandler _target;
    private final ObjectNormalizerFacade _normalizer;
    
    public NormalizingSyncResultsHandler(SyncResultsHandler target,
            ObjectNormalizerFacade normalizer) {
        Assertions.nullCheck(target, "target");
        Assertions.nullCheck(normalizer, "normalizer");
        _target = target;
        _normalizer = normalizer;
    }
    

    public boolean handle(SyncDelta delta) {
        SyncDelta normalized = _normalizer.normalizeSyncDelta(delta);
        return _target.handle(normalized);
    }

}
