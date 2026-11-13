package dev.mikael.forreals.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureOrderTest {
	private static final List<String> FOREST = List.of("lichen", "forest_flowers", "oaks", "bush", "flowers", "grass", "mushrooms");
	private static final List<String> TAIGA = List.of("lichen", "ferns", "spruces", "flowers", "mushrooms", "berries");
	private static final List<String> BIRCH_FOREST = List.of("lichen", "birches", "bush", "flowers", "grass");
	private static final List<String> LATE_FERNS = List.of("bush", "ferns");

	private static void assertAgrees(final List<String> order, final List<String> list) {
		for (int i = 1; i < list.size(); i++) {
			assertTrue(order.indexOf(list.get(i - 1)) < order.indexOf(list.get(i)), list.get(i - 1) + " should come before " + list.get(i) + " in " + order);
		}
	}

	@Test
	void theOrderAgreesWithEveryList() {
		List<List<String>> lists = List.of(FOREST, TAIGA, BIRCH_FOREST, LATE_FERNS);
		List<String> order = FeatureOrder.agreeingWith(lists);
		lists.forEach(list -> assertAgrees(order, list));
	}

	@Test
	void aConstraintSeenLateStillCounts() {
		List<String> order = FeatureOrder.agreeingWith(List.of(TAIGA, FOREST, LATE_FERNS));
		assertTrue(order.indexOf("bush") < order.indexOf("ferns"));
	}

	@Test
	void everyFeatureAppearsOnce() {
		List<String> order = FeatureOrder.agreeingWith(List.of(FOREST, TAIGA, FOREST));
		assertEquals(order.size(), order.stream().distinct().count());
		assertEquals(10, order.size());
	}

	@Test
	void oneListKeepsItsOrder() {
		assertEquals(FOREST, FeatureOrder.agreeingWith(List.of(FOREST)));
	}
}
