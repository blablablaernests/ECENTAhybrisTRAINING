/**
 * Copyright (c) 2019 SAP SE or an SAP affiliate company. All rights reserved.
 */
package com.hybris.merchandising.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.ui.ModelMap;

import com.hybris.merchandising.constants.MerchandisingConstants;
import com.hybris.merchandising.context.ContextService;
import com.hybris.merchandising.context.impl.DefaultContextRepository;
import com.hybris.merchandising.context.impl.DefaultContextService;
import com.hybris.merchandising.model.Facet;

import de.hybris.platform.acceleratorservices.data.RequestContextData;
import de.hybris.platform.category.model.CategoryModel;
import de.hybris.platform.cms2.model.pages.ContentPageModel;
import de.hybris.platform.commerceservices.search.facetdata.BreadcrumbData;
import de.hybris.platform.commerceservices.search.facetdata.ProductCategorySearchPageData;
import de.hybris.platform.servicelayer.session.Session;
import de.hybris.platform.servicelayer.session.impl.DefaultSession;


/**
 * Test class for {@link HybrisMerchandisingBeforeViewHandlerAdaptee}
 *
 */
public class HybrisMerchandisingBeforeViewHandlerAdapteeTest
{
	private HybrisMerchandisingBeforeViewHandlerAdaptee adaptee;
	private ContextService contextService;

	private static final String CATEGORY_CODE = "MyCode";
	private static final String SEARCH_PAGE_DATA = "searchPageData";

	@Before
	public void setUp()
	{
		adaptee = new HybrisMerchandisingBeforeViewHandlerAdaptee();
		contextService = Mockito.mock(DefaultContextService.class);
		adaptee.setContextService(contextService);
		Mockito.when(contextService.getContextRepository()).thenReturn(new DefaultContextRepository());
	}

	@Test
	public <STATE, RESULT, CATEGORY> void testStoreNavigatedCategory()
	{
		final RequestContextData mockRequestContextData = Mockito.mock(RequestContextData.class);
		final Session mockSession = new EnhancedMockSession();
		final ModelMap mockModelMap = Mockito.mock(ModelMap.class);

		final ProductCategorySearchPageData<STATE, RESULT, CATEGORY> searchPageData = new ProductCategorySearchPageData<>();
		Mockito.when(mockModelMap.get("searchPageData")).thenReturn(searchPageData);

		final CategoryModel category = new CategoryModel();
		category.setCode(CATEGORY_CODE);
		Mockito.when(mockRequestContextData.getCategory()).thenReturn(category);

		final ContentPageModel homePage = new ContentPageModel();
		homePage.setUid("homepage");
		homePage.setHomepage(true);

		final ContentPageModel otherPage = new ContentPageModel();
		otherPage.setUid("categorypage");
		otherPage.setHomepage(false);

		//Test storing a category when we have the category provided and are on a search result page.
		adaptee.storeCategory(mockRequestContextData, mockSession, mockModelMap);
		Map<String, Object> attributes = mockSession.getAllAttributes();
		Assert.assertEquals("Attributes should have 1 value in it as we have a category", 1, attributes.values().size());
		Assert.assertNotNull("A result should be returned for key",
				attributes.get(MerchandisingConstants.PAGE_CONTEXT_CATEGORY));
		Assert.assertEquals("Attribute value should be 'MyCode'", CATEGORY_CODE,
				attributes.get(MerchandisingConstants.PAGE_CONTEXT_CATEGORY));

		//Test being on a search result page without a category.
		Mockito.when(mockRequestContextData.getCategory()).thenReturn(null);
		adaptee.storeCategory(mockRequestContextData, mockSession, mockModelMap);
		attributes = mockSession.getAllAttributes();
		Assert.assertEquals("Attributes should have 0 values in it as we have a null category", 0, attributes.values().size());

		//Test being on a page other than a search results page but not the home page.
		mockSession.setAttribute(MerchandisingConstants.PAGE_CONTEXT_CATEGORY, "TESTVALUE");
		Mockito.when(mockModelMap.get("cmsPage")).thenReturn(otherPage);
		Mockito.when(mockModelMap.get("searchPageData")).thenReturn(null);
		adaptee.storeCategory(mockRequestContextData, mockSession, mockModelMap);
		attributes = mockSession.getAllAttributes();
		Assert.assertEquals(
				"Attributes should have 1 value in it as we did not have an instance of searchPageData but are not on homepage", 1,
				attributes.values().size());

		//Test being not on the homepage. This should effectively be a no-op case.
		Mockito.when(mockModelMap.get("cmsPage")).thenReturn(searchPageData);
		Mockito.when(mockModelMap.get("searchPageData")).thenReturn(null);
		adaptee.storeCategory(mockRequestContextData, mockSession, mockModelMap);
		attributes = mockSession.getAllAttributes();
		Assert.assertEquals(
				"Attributes should have 0 value in it as we did not have an instance of searchPageData but are not on homepage", 1,
				attributes.values().size());

		//Test being on the homepage.
		Mockito.when(mockModelMap.get("cmsPage")).thenReturn(homePage);
		Mockito.when(mockModelMap.get("searchPageData")).thenReturn(homePage);
		adaptee.storeCategory(mockRequestContextData, mockSession, mockModelMap);
		attributes = mockSession.getAllAttributes();
		Assert.assertEquals(
				"Attributes should have 0 values in it as we did not have an instance of searchPageData but are on homepage",
				0,
				attributes.values().size());
	}

	@Test
	public void testStoreFacets() {
		final Session mockSession = new EnhancedMockSession();
		final ModelMap mockModelMap = Mockito.mock(ModelMap.class);

		final ProductCategorySearchPageData data = new ProductCategorySearchPageData();
		final BreadcrumbData bread1 = new BreadcrumbData();
		bread1.setFacetCode("bread1");
		bread1.setFacetName("bread1");
		bread1.setFacetValueName("bread1value");
		bread1.setFacetValueCode("bread1value");

		final BreadcrumbData bread2 = new BreadcrumbData();
		bread2.setFacetCode("bread2");
		bread2.setFacetName("bread2");
		bread2.setFacetValueName("bread2value");
		bread2.setFacetValueCode("bread2value");

		data.setBreadcrumbs(new ArrayList<BreadcrumbData>(Arrays.asList(bread1, bread2)));
		Mockito.when(mockModelMap.get(SEARCH_PAGE_DATA)).thenReturn(data);
		adaptee.storeFacets(mockSession, mockModelMap);

		@SuppressWarnings("unchecked")
		final List<Facet> facets = (List<Facet>) contextService.getContextRepository()
				.get(MerchandisingConstants.PAGE_CONTEXT_FACETS)
				.getProperty(MerchandisingConstants.PAGE_CONTEXT_FACETS);
		assertNotNull("Expected facets to not be null", facets);
	}

	@Test
	public <STATE> void testGetFacetBreadcrumbs() {
		final String facetCode = "bd1_code";
		final BreadcrumbData<STATE> bd1 = new BreadcrumbData<>();
		bd1.setFacetCode("bd1_code");
		bd1.setFacetName("bd1_name");
		bd1.setFacetValueName("bd1_valuename");
		bd1.setFacetValueCode("bd1_valuecode");

		final List<BreadcrumbData<STATE>> breadcrumbData = new ArrayList<>(Arrays.asList(bd1));
		final Map<String, Facet> facets = adaptee.getFacetBreadcrumbs(breadcrumbData);
		assertNotNull("Expected facets to not be null", facets);
		facets.forEach((key, facet) -> {
			assertEquals("Expected facet code to be set", facetCode, facet.getCode());
		});
	}

	/**
	 * EnhancedMockSession is an enhanced implementation of the default hybris mock session. It does not extend the
	 * MockSession as we are unable to get visibility of the attributes map.
	 *
	 */
	class EnhancedMockSession extends DefaultSession
	{
		private static final long serialVersionUID = 1L;
		private long sessionIdCounter = 1L;
			private final Map<String, Object> attributes = new ConcurrentHashMap<>();
		private final String sessionId;
	
		@Override
		public String getSessionId()
		{
			return this.sessionId;
		}
	
		public EnhancedMockSession()
		{
			this.sessionId = String.valueOf(this.sessionIdCounter++);
		}
	
		@Override
		public Map<String, Object> getAllAttributes()
		{
			return Collections.unmodifiableMap(this.attributes);
		}
	
		@Override
		public Object getAttribute(final String name)
		{
			return this.attributes.get(name);
		}
	
		@Override
		public void setAttribute(final String name, final Object value)
		{
			this.attributes.put(name, value);
		}
	
		@Override
		public void removeAttribute(final String name)
		{
			this.attributes.remove(name);
		}
	}
}
