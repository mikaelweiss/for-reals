package dev.mikael.forreals.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds one order for features that agrees with every biome's own list. The game demands that any two features appear
 * in the same order in every biome that has both. A new biome whose features follow this order can never break that.
 */
public final class FeatureOrder {
	private FeatureOrder() {
	}

	/**
	 * @param lists every existing biome's features for one generation step, each in that biome's order
	 * @return every feature once, each after everything any list puts before it. Features that no list relates keep
	 *     the order they were first seen in.
	 */
	public static <T> List<T> agreeingWith(final List<List<T>> lists) {
		Map<T, Set<T>> after = new LinkedHashMap<>();
		Map<T, Integer> waitingOn = new HashMap<>();
		for (List<T> list : lists) {
			for (int i = 0; i < list.size(); i++) {
				after.computeIfAbsent(list.get(i), feature -> new LinkedHashSet<>());
				waitingOn.putIfAbsent(list.get(i), 0);
				if (i > 0 && !list.get(i - 1).equals(list.get(i)) && after.get(list.get(i - 1)).add(list.get(i))) {
					waitingOn.merge(list.get(i), 1, Integer::sum);
				}
			}
		}

		List<T> order = new ArrayList<>(after.size());
		List<T> remaining = new ArrayList<>(after.keySet());
		while (!remaining.isEmpty()) {
			T next = remaining.stream().filter(feature -> waitingOn.get(feature) == 0).findFirst().orElse(remaining.getFirst());
			remaining.remove(next);
			order.add(next);
			after.get(next).forEach(later -> waitingOn.merge(later, -1, Integer::sum));
		}

		return order;
	}
}
