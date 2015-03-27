/*******************************************************************************
 * Copyright (c) 2010-2015 Torsten Hildebrandt and jasima contributors
 *
 * This file is part of jasima, v1.2.
 *
 * jasima is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jasima is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with jasima.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package jasima_gui;

import jasima_gui.util.TypeUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;

import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.javabean.JavaBeanConverter;
import com.thoughtworks.xstream.converters.javabean.JavaBeanProvider;
import com.thoughtworks.xstream.converters.reflection.MissingFieldException;
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

public class PermissiveBeanConverter extends JavaBeanConverter {
	protected static final String NULL_ATTRIBUTE_NAME = "is-null";
	protected static final String NULL_ATTRIBUTE_VALUE = "yes";
	protected ConversionReport report = null;

	public PermissiveBeanConverter(Mapper mapper) {
		super(mapper);
	}

	public void startConversionReport() {
		assert report == null;
		report = new ConversionReport();
	}

	public ConversionReport finishConversionReport() {
		ConversionReport retVal = (report.isEmpty() ? null : report);
		report.finish();
		report = null;
		return retVal;
	}

	public void marshal(final Object source, final HierarchicalStreamWriter writer, final MarshallingContext context) {
		final String classAttributeName = mapper.aliasForSystemAttribute("class");
		beanProvider.visitSerializableProperties(source, new JavaBeanProvider.Visitor() {
			@SuppressWarnings("rawtypes")
			public boolean shouldVisit(String name, Class definedIn) {
				return mapper.shouldSerializeMember(definedIn, name);
			}

			@SuppressWarnings("rawtypes")
			public void visit(String propertyName, Class fieldType, Class definedIn, Object newObj) {
				if (newObj == null) {
					writer.startNode(propertyName);
					writer.addAttribute(NULL_ATTRIBUTE_NAME, NULL_ATTRIBUTE_VALUE);
					writer.endNode();
				} else {
					Class<?> actualType = newObj.getClass();
					String serializedMember = mapper.serializedMember(source.getClass(), propertyName);
					ExtendedHierarchicalStreamWriterHelper.startNode(writer, serializedMember, actualType);
					writer.addAttribute(classAttributeName, mapper.serializedClass(actualType));
					context.convertAnother(newObj);

					writer.endNode();
				}
			}
		});
	}

	public Object unmarshal(final HierarchicalStreamReader reader, final UnmarshallingContext context) {
		final Object result = instantiate(context);

		@SuppressWarnings("serial")
		final Set<String> seenProperties = new HashSet<String>() {
			public boolean add(String e) {
				if (!super.add(e)) {
					throw new DuplicatePropertyException(e);
				}
				return true;
			}
		};

		Class<?> resultType = result.getClass();
		while (reader.hasMoreChildren()) {
			reader.moveDown();

			String propertyName = mapper.realMember(resultType, reader.getNodeName());

			if (mapper.shouldSerializeMember(resultType, propertyName)) {
				seenProperties.add(propertyName);

				boolean propertyExistsInClass;
				try {
					// BeanProvider.propertyDefinedInClass either returns true
					// or throws MissingFieldException (is that really
					// intended behaviour?)
					propertyExistsInClass = beanProvider.propertyDefinedInClass(propertyName, resultType);
				} catch (MissingFieldException e) {
					propertyExistsInClass = false;
				}

				if (propertyExistsInClass) {
					Object value;
					if (NULL_ATTRIBUTE_VALUE.equals(reader.getAttribute(NULL_ATTRIBUTE_NAME))) {
						value = null;
					} else {
						Class<?> type = determineType(reader, result, propertyName);
						Class<?> propType = beanProvider.getPropertyType(result, propertyName);

						if (type.isPrimitive())
							type = TypeUtil.getPrimitiveWrapper(type);

						if (propType.isPrimitive())
							propType = TypeUtil.getPrimitiveWrapper(propType);

						if (!propType.isAssignableFrom(type)) {
							report.propertyTypeChanged(resultType, propertyName);
						} else {
							try {
								value = context.convertAnother(result, type);
								try {
									beanProvider.writeProperty(result, propertyName, value);
								} catch (Exception e) {
									Throwable t = e.getCause();
									if(t instanceof InvocationTargetException) {
										t = t.getCause();
									}
									report.propertyRangeChanged(resultType, propertyName, t.toString());
								}
							} catch (ConversionException e) {
								report.propertyTypeChanged(resultType, propertyName);
							}
						}
					}
				} else {
					report.propertyDisappeared(resultType, propertyName);
				}
			}
			reader.moveUp();
		}

		// check if all properties were seen
		beanProvider.visitSerializableProperties(result, new JavaBeanProvider.Visitor() {
			@SuppressWarnings("rawtypes")
			public boolean shouldVisit(String name, Class definedIn) {
				return mapper.shouldSerializeMember(definedIn, name);
			}

			@SuppressWarnings("rawtypes")
			public void visit(String propertyName, Class fieldType, Class definedIn, Object newObj) {
				if (!seenProperties.contains(propertyName)) {
					report.newProperty(definedIn, propertyName);
				}
			}
		});

		return result;
	}

	protected Object instantiate(UnmarshallingContext context) {
		Object result = context.currentObject();
		if (result == null) {
			result = beanProvider.newInstance(context.getRequiredType());
		}
		return result;
	}

	protected Class<?> determineType(HierarchicalStreamReader reader, Object result, String fieldName) {
		final String classAttributeName = mapper.aliasForSystemAttribute("class");
		String classAttribute = classAttributeName == null ? null : reader.getAttribute(classAttributeName);
		if (classAttribute != null) {
			return mapper.realClass(classAttribute);
		} else {
			// doesn't happen when the file was saved with this Converter
			return mapper.defaultImplementationOf(beanProvider.getPropertyType(result, fieldName));
		}
	}

}
