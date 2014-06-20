using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net.Sockets;
using System.Reflection;
using Org.IdentityConnectors.Common;
using Org.IdentityConnectors.Common.Script;
using Org.IdentityConnectors.Common.Security;
using Org.IdentityConnectors.Framework.Common.Exceptions;
using Org.IdentityConnectors.Framework.Common.Objects;
using Org.IdentityConnectors.Framework.Common.Objects.Filters;
using Org.IdentityConnectors.Framework.Spi;
using Org.IdentityConnectors.Framework.Spi.Operations;

namespace Org.ForgeRock.OpenICF.Connectors.Sample
{
    [ConfigurationClass(true, new[] {"InternalOnly"})]
    public class SampleConfiguration : AbstractConfiguration, StatefulConfiguration
    {
        // Exposed configuration properties.

        /// <summary>
        ///     The connector to connect to.
        /// </summary>
        [ConfigurationProperty(Order = 1, DisplayMessageKey = "Host.display", GroupMessageKey = "Basic.group",
            HelpMessageKey = "Host.help",
            Required = true, Confidential = false)]
        public string Host { get; set; }

        /// <summary>
        ///     The Remote user to authenticate with.
        /// </summary>
        [ConfigurationProperty(Order = 2, DisplayMessageKey = "RemoteUser.display", GroupMessageKey = "Basic.group",
            HelpMessageKey = "RemoteUser.help",
            Required = true, Confidential = false)]
        public string RemoteUser { get; set; }

        /// <summary>
        ///     The Password to authenticate with.
        /// </summary>
        [ConfigurationProperty(Order = 3, DisplayMessageKey = "Password.display", GroupMessageKey = "Basic.group",
            HelpMessageKey = "Password.help",
            Required = true, Confidential = false)]
        public GuardedString Password { get; set; }

        public string InternalOnly { get; set; }

        /// <summary>
        ///     {@inheritDoc}
        /// </summary>
        public override void Validate()
        {
            if (String.IsNullOrEmpty(Host))
            {
                throw new ArgumentException("Host cannot be null or empty.");
            }

            Assertions.BlankCheck(RemoteUser, "RemoteUser");

            Assertions.NullCheck(Password, "Password");
        }

        public virtual void Release()
        {
        }
    }

    [ConnectorClass("connector_displayName",
        typeof (SampleConfiguration),
        MessageCatalogPaths = new[] {"Org.ForgeRock.OpenICF.Connectors.Sample.Messages"}
        )]
    public class SampleConnector : PoolableConnector, AttributeNormalizer, AuthenticateOp, CreateOp, DeleteOp,
        ResolveUsernameOp, SchemaOp, ScriptOnConnectorOp, ScriptOnResourceOp, SearchOp<String>, SyncOp, TestOp,
        UpdateAttributeValuesOp

    {
        /// <summary>
        ///     Place holder for the <seealso cref="Configuration" /> passed into the init() method
        ///     <seealso cref="SampleConnector#init(org.identityconnectors.framework.spi.Configuration)" />.
        /// </summary>
        private SampleConfiguration _configuration;

        private Schema _schema;

        /// <summary>
        ///     Gets the Configuration context for this connector.
        /// </summary>
        /// <returns> The current <seealso cref="Configuration" /> </returns>
        public virtual Configuration Configuration
        {
            get { return _configuration; }
        }

        public virtual ConnectorAttribute NormalizeAttribute(ObjectClass oclass, ConnectorAttribute attribute)
        {
            if (ConnectorAttributeUtil.NamesEqual(attribute.Name, Uid.NAME))
            {
                return new Uid(ConnectorAttributeUtil.GetStringValue(attribute).ToLower());
            }
            return attribute;
        }

        /// <summary>
        ///     ****************
        ///     SPI Operations
        ///     Implement the following operations using the contract and
        ///     description found in the Javadoc for these methods.
        ///     *****************
        /// </summary>
        public virtual Uid Authenticate(ObjectClass objectClass, string userName, GuardedString password,
            OperationOptions options)
        {
            if (ObjectClass.ACCOUNT.Equals(objectClass))
            {
                return new Uid(userName);
            }
            Trace.TraceWarning("Authenticate of type {0} is not supported",
                _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                    objectClass.GetObjectClassValue()));
            throw new NotSupportedException("Authenticate of type" + objectClass.GetObjectClassValue() +
                                            " is not supported");
        }

        public virtual Uid Create(ObjectClass objectClass, ICollection<ConnectorAttribute> createAttributes,
            OperationOptions options)
        {
            if (ObjectClass.ACCOUNT.Equals(objectClass) || ObjectClass.GROUP.Equals(objectClass))
            {
                Name name = ConnectorAttributeUtil.GetNameFromAttributes(createAttributes);
                if (name != null)
                {
                    // do real create here
                    return new Uid(ConnectorAttributeUtil.GetStringValue(name).ToLower());
                }
                throw new InvalidAttributeValueException("Name attribute is required");
            }
            Trace.TraceWarning("Delete of type {0} is not supported",
                _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                    objectClass.GetObjectClassValue()));
            throw new NotSupportedException("Delete of type" + objectClass.GetObjectClassValue() + " is not supported");
        }

        public virtual void Delete(ObjectClass objectClass, Uid uid, OperationOptions options)
        {
            if (ObjectClass.ACCOUNT.Equals(objectClass) || ObjectClass.GROUP.Equals(objectClass))
            {
                // do real delete here
            }
            else
            {
                Trace.TraceWarning("Delete of type {0} is not supported",
                    _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                        objectClass.GetObjectClassValue()));
                throw new NotSupportedException("Delete of type" + objectClass.GetObjectClassValue() +
                                                " is not supported");
            }
        }

        /// <summary>
        ///     Callback method to receive the <seealso cref="Configuration" />.
        /// </summary>
        /// <param name="configuration"> the new <seealso cref="Configuration" /> </param>
        /// <seealso
        ///     cref="Org.IdentityConnectors.Framework.Spi.Connector.Init( Org.IdentityConnectors.Framework.Spi.Configuration)">
        /// </seealso>
        public virtual void Init(Configuration configuration)
        {
            _configuration = (SampleConfiguration) configuration;
        }

        /// <summary>
        ///     Disposes of the <seealso cref="SampleConnector" />'s resources.
        /// </summary>
        /// <seealso cref="Org.IdentityConnectors.Framework.Spi.Connector# dispose()"></seealso>
        public virtual void Dispose()
        {
            _configuration = null;
        }

        public virtual void CheckAlive()
        {
            // Do some cheap operartion to verify it's a healty Connector instance from the pool.
        }

        public virtual Uid ResolveUsername(ObjectClass objectClass, string userName, OperationOptions options)
        {
            if (ObjectClass.ACCOUNT.Equals(objectClass))
            {
                return new Uid(userName);
            }
            Trace.TraceWarning("ResolveUsername of type {0} is not supported",
                _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                    objectClass.GetObjectClassValue()));
            throw new NotSupportedException("ResolveUsername of type" + objectClass.GetObjectClassValue() +
                                            " is not supported");
        }

        public virtual Schema Schema()
        {
            if (null == _schema)
            {
                var builder = new SchemaBuilder(SafeType<Connector>.Get(this));
                // Account
                var accountInfoBuilder = new ObjectClassInfoBuilder();
                accountInfoBuilder.AddAttributeInfo(Name.INFO);
                accountInfoBuilder.AddAttributeInfo(OperationalAttributeInfos.PASSWORD);
                accountInfoBuilder.AddAttributeInfo(PredefinedAttributeInfos.GROUPS);
                accountInfoBuilder.AddAttributeInfo(ConnectorAttributeInfoBuilder.Build("firstName"));
                accountInfoBuilder.AddAttributeInfo(
                    ConnectorAttributeInfoBuilder.Define("lastName").SetRequired(true).Build());
                builder.DefineObjectClass(accountInfoBuilder.Build());

                // Group
                var groupInfoBuilder = new ObjectClassInfoBuilder();
                groupInfoBuilder.ObjectType = ObjectClass.GROUP_NAME;
                groupInfoBuilder.AddAttributeInfo(Name.INFO);
                groupInfoBuilder.AddAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);
                groupInfoBuilder.AddAttributeInfo(
                    ConnectorAttributeInfoBuilder.Define("members")
                        .SetCreatable(false)
                        .SetUpdateable(false)
                        .SetMultiValued(true)
                        .Build());

                // Only the CRUD operations
                builder.DefineObjectClass(groupInfoBuilder.Build(), SafeType<SPIOperation>.Get<CreateOp>(),
                    SafeType<SPIOperation>.ForRawType(typeof (SearchOp<>)), SafeType<SPIOperation>.Get<UpdateOp>(),
                    SafeType<SPIOperation>.Get<DeleteOp>());

                // Operation Options
                builder.DefineOperationOption(OperationOptionInfoBuilder.BuildAttributesToGet(),
                    SafeType<SPIOperation>.ForRawType(typeof (SearchOp<>)));

                // Support paged Search
                builder.DefineOperationOption(OperationOptionInfoBuilder.BuildPageSize(),
                    SafeType<SPIOperation>.ForRawType(typeof (SearchOp<>)));
                builder.DefineOperationOption(OperationOptionInfoBuilder.BuildPagedResultsCookie(),
                    SafeType<SPIOperation>.ForRawType(typeof (SearchOp<>)));

                // Support to execute operation with provided credentials
                builder.DefineOperationOption(OperationOptionInfoBuilder.BuildRunAsUser());
                builder.DefineOperationOption(OperationOptionInfoBuilder.BuildRunWithPassword());

                _schema = builder.Build();
            }
            return _schema;
        }

        public virtual object RunScriptOnConnector(ScriptContext request, OperationOptions options)
        {
            ScriptExecutorFactory factory = ScriptExecutorFactory.NewInstance(request.ScriptLanguage);
            ScriptExecutor executor = factory.NewScriptExecutor(new[] {Assembly.GetExecutingAssembly()},
                request.ScriptText, true);

            if (String.IsNullOrEmpty(options.RunAsUser))
            {
                string password = SecurityUtil.Decrypt(options.RunWithPassword);
                // Use these to execute the script with these credentials
                Debug.Assert(password != null, "Password is required");
            }
            try
            {
                return executor.Execute(request.ScriptArguments);
            }
            catch (Exception e)
            {
                TraceUtil.TraceException("Failed to execute Script", e);
                throw;
            }
        }

        public virtual object RunScriptOnResource(ScriptContext request, OperationOptions options)
        {
            try
            {
                // Execute the script on remote resource
                if (String.IsNullOrEmpty(options.RunAsUser))
                {
                    string password = SecurityUtil.Decrypt(options.RunWithPassword);
                    // Use these to execute the script with these credentials
                    Debug.Assert(password != null, "Password is required");
                    return options.RunAsUser;
                }
                throw new SocketException();
            }
            catch (Exception e)
            {
                TraceUtil.TraceException("Failed to execute Script", e);
                throw;
            }
        }

        public virtual FilterTranslator<string> CreateFilterTranslator(ObjectClass objectClass, OperationOptions options)
        {
            return new SampleFilterTranslator();
        }

        public virtual void ExecuteQuery(ObjectClass objectClass, string query, ResultsHandler handler,
            OperationOptions options)
        {
            var builder = new ConnectorObjectBuilder();
            builder.SetUid("3f50eca0-f5e9-11e3-a3ac-0800200c9a66");
            builder.SetName("Foo");
            builder.AddAttribute(ConnectorAttributeBuilder.BuildEnabled(true));

            foreach (ConnectorObject connectorObject in CollectionUtil.NewSet(builder.Build()))
            {
                if (!handler.Handle(connectorObject))
                {
                    // Stop iterating because the handler stopped processing
                    break;
                }
            }
            if (options.PageSize != null && 0 < options.PageSize)
            {
                Trace.TraceInformation("Paged Search was requested");
                ((SearchResultsHandler) handler).HandleResult(new SearchResult("0", 0));
            }
        }

        public virtual void Sync(ObjectClass objectClass, SyncToken token, SyncResultsHandler handler,
            OperationOptions options)
        {
            if (ObjectClass.ALL.Equals(objectClass))
            {
                //
            }
            else if (ObjectClass.ACCOUNT.Equals(objectClass))
            {
                var builder = new ConnectorObjectBuilder();
                builder.SetUid("3f50eca0-f5e9-11e3-a3ac-0800200c9a66");
                builder.SetName("Foo");
                builder.AddAttribute(ConnectorAttributeBuilder.BuildEnabled(true));

                var deltaBuilder = new SyncDeltaBuilder
                {
                    Object = builder.Build(),
                    DeltaType = SyncDeltaType.CREATE,
                    Token = new SyncToken(10)
                };

                foreach (SyncDelta connectorObject in CollectionUtil.NewSet(deltaBuilder.Build()))
                {
                    if (!handler.Handle(connectorObject))
                    {
                        // Stop iterating because the handler stopped processing
                        break;
                    }
                }
            }
            else
            {
                Trace.TraceWarning("Sync of type {0} is not supported",
                    _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                        objectClass.GetObjectClassValue()));
                throw new NotSupportedException("Sync of type" + objectClass.GetObjectClassValue() + " is not supported");
            }
            ((SyncTokenResultsHandler) handler).HandleResult(new SyncToken(10));
        }

        public virtual SyncToken GetLatestSyncToken(ObjectClass objectClass)
        {
            if (ObjectClass.ACCOUNT.Equals(objectClass))
            {
                return new SyncToken(10);
            }
            Trace.TraceWarning("Sync of type {0} is not supported",
                _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                    objectClass.GetObjectClassValue()));
            throw new NotSupportedException("Sync of type" + objectClass.GetObjectClassValue() + " is not supported");
        }

        public virtual void Test()
        {
            Debug.Write("Test works well");
        }

        public virtual Uid Update(ObjectClass objectClass, Uid uid, ICollection<ConnectorAttribute> replaceAttributes,
            OperationOptions options)
        {
            var attributesAccessor = new ConnectorAttributesAccessor(replaceAttributes);
            Name newName = attributesAccessor.GetName();
            Uid uidAfterUpdate = uid;
            if (newName != null)
            {
                Trace.TraceInformation("Rename the object {0}:{1} to {2}", objectClass.GetObjectClassValue(),
                    uid.GetUidValue(), newName.GetNameValue());
                uidAfterUpdate = new Uid(newName.GetNameValue().ToLower());
            }

            if (ObjectClass.ACCOUNT.Equals(objectClass))
            {
            }
            else if (ObjectClass.GROUP.Is(objectClass.GetObjectClassValue()))
            {
                if (attributesAccessor.HasAttribute("members"))
                {
                    throw new InvalidAttributeValueException("Requested to update a read only attribute");
                }
            }
            else
            {
                Trace.TraceWarning("Update of type {0} is not supported",
                    _configuration.ConnectorMessages.Format(objectClass.GetDisplayNameKey(),
                        objectClass.GetObjectClassValue()));
                throw new NotSupportedException("Update of type" + objectClass.GetObjectClassValue() +
                                                " is not supported");
            }
            return uidAfterUpdate;
        }

        public Uid AddAttributeValues(ObjectClass objectClass, Uid uid, ICollection<ConnectorAttribute> valuesToAdd,
            OperationOptions options)
        {
            return uid;
        }

        public Uid RemoveAttributeValues(ObjectClass objectClass, Uid uid,
            ICollection<ConnectorAttribute> valuesToRemove, OperationOptions options)
        {
            return uid;
        }
    }

    public class SampleFilterTranslator : AbstractFilterTranslator<string>
    {
    }
}