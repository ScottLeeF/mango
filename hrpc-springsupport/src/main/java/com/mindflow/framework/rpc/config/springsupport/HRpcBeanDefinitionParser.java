package com.mindflow.framework.rpc.config.springsupport;

import com.mindflow.framework.rpc.config.ProtocolConfig;
import com.mindflow.framework.rpc.config.RegistryConfig;
import com.mindflow.framework.rpc.config.ServiceConfig;
import com.mindflow.framework.rpc.util.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;

/**
 * ${DESCRIPTION}
 *
 * @author Ricky Fung
 */
public class HRpcBeanDefinitionParser implements BeanDefinitionParser {

    private final Class<?> beanClass;

    private final boolean required;

    public HRpcBeanDefinitionParser(Class<?> beanClass, boolean required) {
        this.beanClass = beanClass;
        this.required = required;
    }

    @Override
    public BeanDefinition parse(Element element, ParserContext parserContext) {
        try {
            return parse(element, parserContext, beanClass, required);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BeanDefinition parse(Element element, ParserContext parserContext, Class<?> beanClass, boolean required)
            throws ClassNotFoundException {
        RootBeanDefinition bd = new RootBeanDefinition();
        bd.setBeanClass(beanClass);
        // 不允许lazy init
        bd.setLazyInit(false);

        // 如果没有id则按照规则生成一个id,注册id到context中
        String id = element.getAttribute("id");
        if ((id == null || id.length() == 0) && required) {
            String generatedBeanName = element.getAttribute("name");
            if (generatedBeanName == null || generatedBeanName.length() == 0) {
                generatedBeanName = element.getAttribute("interface");
            }
            if (generatedBeanName == null || generatedBeanName.length() == 0) {
                generatedBeanName = beanClass.getName();
            }
            id = generatedBeanName;
            int counter = 2;
            while (parserContext.getRegistry().containsBeanDefinition(id)) {
                id = generatedBeanName + (counter++);
            }
        }
        if (id != null && id.length() > 0) {
            if (parserContext.getRegistry().containsBeanDefinition(id)) {
                throw new IllegalStateException("Duplicate spring bean id " + id);
            }
            parserContext.getRegistry().registerBeanDefinition(id, bd);
        }
        bd.getPropertyValues().addPropertyValue("id", id);

        //解析属性
        if (ProtocolConfig.class.equals(beanClass)) {
            HRpcNamespaceHandler.protocolDefineNames.add(id);

            parseCommonProperty("name", null, element, bd, parserContext);
            parseCommonProperty("host", null, element, bd, parserContext);
            parseCommonProperty("port", null, element, bd, parserContext);
            parseCommonProperty("codec", null, element, bd, parserContext);
            parseCommonProperty("serialization", null, element, bd, parserContext);
            parseCommonProperty("pool-type", "poolType", element, bd, parserContext);
            parseCommonProperty("min-pool-size", "minPoolSize", element, bd, parserContext);
            parseCommonProperty("max-pool-size", "maxPoolSize", element, bd, parserContext);
            parseCommonProperty("charset", null, element, bd, parserContext);
            parseCommonProperty("buffer-size", "bufferSize", element, bd, parserContext);

        } else if (RegistryConfig.class.equals(beanClass)) {
            HRpcNamespaceHandler.registryDefineNames.add(id);

            parseCommonProperty("protocol", null, element, bd, parserContext);
            parseCommonProperty("address", null, element, bd, parserContext);
            parseCommonProperty("timeout", null, element, bd, parserContext);
            parseCommonProperty("session-timeout", "sessionTimeout", element, bd, parserContext);
            parseCommonProperty("username", null, element, bd, parserContext);
            parseCommonProperty("password", null, element, bd, parserContext);

        } else if (ReferenceConfigBean.class.equals(beanClass)) {
            HRpcNamespaceHandler.referenceConfigDefineNames.add(id);

            parseCommonProperty("interface", "interfaceName", element, bd, parserContext);

            String registry = element.getAttribute("registry");
            if (StringUtils.isNotBlank(registry)) {
                parseMultiRef("registries", registry, bd, parserContext);
            }

            parseCommonProperty("group", null, element, bd, parserContext);
            parseCommonProperty("version", null, element, bd, parserContext);

            parseCommonProperty("timeout", null, element, bd, parserContext);
            parseCommonProperty("retries", null, element, bd, parserContext);

        } else if (ServiceConfig.class.equals(beanClass)) {
            HRpcNamespaceHandler.serviceConfigDefineNames.add(id);

            parseCommonProperty("interface", "interfaceName", element, bd, parserContext);

            parseSingleRef("ref", element, bd, parserContext);

            String registry = element.getAttribute("registry");
            if (StringUtils.isNotBlank(registry)) {
                parseMultiRef("registries", registry, bd, parserContext);
            }

            String protocol = element.getAttribute("protocol");
            if (StringUtils.isNotBlank(protocol)) {
                parseMultiRef("protocols", protocol, bd, parserContext);
            }

            parseCommonProperty("timeout", null, element, bd, parserContext);
            parseCommonProperty("retries", null, element, bd, parserContext);

            parseCommonProperty("group", null, element, bd, parserContext);
            parseCommonProperty("version", null, element, bd, parserContext);
        }
        return bd;
    }

    private static void parseCommonProperty(String name, String alias, Element element, BeanDefinition bd,
                                            ParserContext parserContext) {

        String value = element.getAttribute(name);
        if (StringUtils.isNotBlank(value)) {

            String property = alias!=null ? alias:name;

            if("port".equals(name) || "buffer-size".equals(name)
                    || "min-pool-size".equals(name) || "max-pool-size".equals(name)
                    || "timeout".equals(name)|| "session-timeout".equals(name)|| "payload".equals(name)
                    || "retries".equals(name)){

                bd.getPropertyValues().addPropertyValue(property, Integer.parseInt(value));
            } if("default".equals(name) ||"check".equals(name) ||"async".equals(name)) {
                bd.getPropertyValues().addPropertyValue(property, Boolean.valueOf(value));
            } else {
                bd.getPropertyValues().addPropertyValue(property, value);
            }
        }
    }

    private static void parseSingleRef(String property, Element element, BeanDefinition bd,
                                       ParserContext parserContext) {

        String value = element.getAttribute(property);
        if (StringUtils.isNotBlank(value)) {
            if (parserContext.getRegistry().containsBeanDefinition(value)) {
                BeanDefinition refBean = parserContext.getRegistry().getBeanDefinition(value);
                if (!refBean.isSingleton()) {
                    throw new IllegalStateException("The exported service ref " + value + " must be singleton! Please set the " + value
                            + " bean scope to singleton, eg: <bean id=\"" + value + "\" scope=\"singleton\" ...>");
                }
            }
            bd.getPropertyValues().addPropertyValue(property, new RuntimeBeanReference(value));
        }
    }

    private static void parseMultiRef(String property, String value, BeanDefinition bd,
                                      ParserContext parserContext) {
        String[] values = value.split("\\s*[,]+\\s*");
        ManagedList list = null;
        for (int i = 0; i < values.length; i++) {
            String v = values[i];
            if (v != null && v.length() > 0) {
                if (list == null) {
                    list = new ManagedList();
                }
                list.add(new RuntimeBeanReference(v));
            }
        }
        bd.getPropertyValues().addPropertyValue(property, list);
    }
}