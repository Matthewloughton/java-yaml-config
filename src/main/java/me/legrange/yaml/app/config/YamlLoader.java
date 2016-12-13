package me.legrange.yaml.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import me.legrange.yaml.app.config.annotation.NotBlank;
import me.legrange.yaml.app.config.annotation.NotEmpty;
import me.legrange.yaml.app.config.annotation.NotNull;
import org.yaml.snakeyaml.Yaml;

/**
 * Configuration generated from YAML config file.
 *
 * @author gideon
 */
public abstract class YamlLoader {

    /**
     * Read the configuration file and return a configuration object.
     *
     * @param <C>
     * @param fileName The file to read.
     * @param clazz Configuration implementation class to load.
     * @return The configuration object.
     * @throws ConfigurationException Thrown if there is a problem reading or
     * parsing the configuration.
     */
    public static <C extends Configuration> C readConfiguration(String fileName, Class<C> clazz) throws ConfigurationException {
        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(Paths.get(fileName))) {
            C conf = yaml.loadAs(in, clazz);
            validate(conf);
            return conf;
        } catch (IOException ex) {
            throw new ConfigurationException(String.format("Error reading configuraion file '%s': %s", fileName, ex.getMessage()), ex);
        }
    }

    private static void validate(Configuration conf) throws ValidationException {
        for (Field field : conf.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(NotNull.class)) {
                validateNotNull(field, conf);
            }
            if (field.isAnnotationPresent(NotBlank.class)) {
                validateNotBlank(field, conf);
            }
            if (field.isAnnotationPresent(NotEmpty.class)) {
                validateNotEmpty(field, conf);
            }
        }
    }

    private static void validateNotNull(Field field, Object inst) throws ValidationException {
        Object val = get(field, inst);
        if (val == null) {
            throw new ValidationException("%s in %s must not be undefined", field.getName(), inst.getClass().getSimpleName());
        }
    }
    
    private static void validateNotBlank(Field field, Object inst) throws ValidationException {
        validateNotNull(field, inst);
        Object val = get(field, inst);
        if (!(val instanceof String)) {
            throw new ValidationException("%s in %s is not a String as expected", field.getName(), inst.getClass().getSimpleName());
        }
        if (((String)val).isEmpty()) {
            throw new ValidationException("%s in %s must not be blank", field.getName(), inst.getClass().getSimpleName());
        }
    }

    private static void validateNotEmpty(Field field, Object inst) throws ValidationException {
        validateNotNull(field, inst);
        Object val = get(field, inst);
        if (!(val instanceof Collection)) {
            throw new ValidationException("%s in %s is not a collection as expected", field.getName(), inst.getClass().getSimpleName());
        }
        if (((Collection)val).isEmpty()) {
            throw new ValidationException("%s in %s must not be empty", field.getName(), inst.getClass().getSimpleName());
        }
    }


    private static Object get(Field field, Object inst) throws ValidationException {
        if (field.isAccessible()) {
            try {
                return field.get(inst);
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Field '%s' is not found on object '%s'", field.getName(), inst.getClass().getSimpleName());
                
            } catch (IllegalAccessException ex) {
                throw new ValidationException("Field '%s' on '%s' is not accessible", field.getName(), inst.getClass().getSimpleName());
            }
        } else {
            String name = field.getName().substring(0, 1).toUpperCase() + field.getName().substring(1);
            if (field.getType().equals(Boolean.class)) {
                name = "is" + name;
            } else {
                name = "get" + name;
            }
            try {
                Method meth = inst.getClass().getDeclaredMethod(name, new Class[]{});
                return meth.invoke(inst, new Object[]{});
            } catch (NoSuchMethodException ex) {
                throw new ValidationException("Field '%s' on '%s' does not have a get-method", field.getName(), inst.getClass().getSimpleName());
            } catch (SecurityException ex) {
                throw new ValidationException("Method '%s' on '%s' is in-accessible", name, inst.getClass().getSimpleName());
            } catch (IllegalAccessException ex) {
                throw new ValidationException("Method '%s' on '%s' is not accessible", name, inst.getClass().getSimpleName());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Method '%s' is not found on object '%s'", name, inst.getClass().getSimpleName());
            } catch (InvocationTargetException ex) {
                throw new ValidationException(String.format("Error calling '%s' on object '%s': %s", name, inst.getClass().getSimpleName(), ex.getMessage()), ex);
            }
        }
    }


}
