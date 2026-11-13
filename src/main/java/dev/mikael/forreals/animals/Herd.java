package dev.mikael.forreals.animals;

/** One herd or pack. It exists whether or not its land is loaded. */
public final class Herd {
	private final int id;
	private final Species species;
	private double x;
	private double z;
	private int count;
	private double hunger;
	private double fear;

	public Herd(final int id, final Species species, final double x, final double z, final int count, final double hunger, final double fear) {
		this.id = id;
		this.species = species;
		this.x = x;
		this.z = z;
		this.count = count;
		this.hunger = hunger;
		this.fear = fear;
	}

	public int id() {
		return this.id;
	}

	public Species species() {
		return this.species;
	}

	public double x() {
		return this.x;
	}

	public double z() {
		return this.z;
	}

	public void moveTo(final double x, final double z) {
		this.x = x;
		this.z = z;
	}

	public int count() {
		return this.count;
	}

	public void addMembers(final int members) {
		this.count = Math.max(0, this.count + members);
	}

	/** 0 for well fed, 1 for starving. */
	public double hunger() {
		return this.hunger;
	}

	public void setHunger(final double hunger) {
		this.hunger = Math.clamp(hunger, 0, 1);
	}

	/** 0 for calm, 1 for a herd that has learned to run from everything. */
	public double fear() {
		return this.fear;
	}

	public void setFear(final double fear) {
		this.fear = Math.clamp(fear, 0, 1);
	}

	public long range() {
		return Range.keyAt(this.x, this.z);
	}
}
