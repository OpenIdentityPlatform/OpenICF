package org.identityconnectors.framework.spi;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.spi.operations.AdvancedUpdateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.SyncOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;

/**
 * Interface to be implemented by connectors that need
 * to normalize certain attributes. This might, for
 * example, be used to normalize whitespace within 
 * DN's to ensure consistent filtering whether that
 * filtering is natively on the resource or by the
 * connector framework. For connectors implementing
 * this interface, the method {@link #normalizeAttribute(ObjectClass, Attribute)}
 * will be applied to each of the following:
 * <ol>
 *    <li>The filter passed to {@link SearchOp}.</li>
 *    <li>The results returned from {@link SearchOp}.</li>
 *    <li>The results returned from {@link SyncOp}.</li>
 *    <li>The attributes passed to {@link AdvancedUpdateOp}.</li>
 *    <li>The <code>Uid</code> returned from {@link AdvancedUpdateOp}.</li>
 *    <li>The attributes passed to {@link UpdateOp}.</li>
 *    <li>The <code>Uid</code> returned from {@link UpdateOp}.</li>
 *    <li>The attributes passed to {@link CreateOp}.</li>
 *    <li>The <code>Uid</code> returned from {@link CreateOp}.</li>
 *    <li>The <code>Uid</code> passed to {@link org.identityconnectors.framework.spi.operations.DeleteOp}.</li>
 * </ol>
 */
public interface AttributeNormalizer 
{
    public Attribute normalizeAttribute(ObjectClass oclass, Attribute attribute);
}
