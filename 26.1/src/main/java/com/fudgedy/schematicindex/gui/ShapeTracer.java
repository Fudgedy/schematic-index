package com.fudgedy.schematicindex.gui;

public final class ShapeTracer
{
	private static final double EPSILON = 1.0E-7D;

	public static final class Hit
	{
		public int color;
		public int face;
	}

	private ShapeTracer()
	{
	}

	public static boolean trace(BlockShapes.Shape shape, double originX, double originY, double originZ,
			double dirX, double dirY, double dirZ, int lod, Hit hit)
			{
		BlockShapes.Quad[] quads = shape.quads;

		if (quads == null)
		{
			return false;
		}

		double bestDistance = Double.MAX_VALUE;
		int bestColor = 0;
		int bestFace = -1;

		for (BlockShapes.Quad quad : quads)
		{
			for (int triangle = 0; triangle < 2; triangle++)
			{
				int b = triangle == 0 ? 1 : 2;
				int c = triangle == 0 ? 2 : 3;

				double edge1X = quad.xs[b] - quad.xs[0];
				double edge1Y = quad.ys[b] - quad.ys[0];
				double edge1Z = quad.zs[b] - quad.zs[0];
				double edge2X = quad.xs[c] - quad.xs[0];
				double edge2Y = quad.ys[c] - quad.ys[0];
				double edge2Z = quad.zs[c] - quad.zs[0];

				double crossX = dirY * edge2Z - dirZ * edge2Y;
				double crossY = dirZ * edge2X - dirX * edge2Z;
				double crossZ = dirX * edge2Y - dirY * edge2X;

				double determinant = edge1X * crossX + edge1Y * crossY + edge1Z * crossZ;

				if (determinant > -EPSILON && determinant < EPSILON)
				{
					continue;
				}

				double inverse = 1.0D / determinant;
				double toOriginX = originX - quad.xs[0];
				double toOriginY = originY - quad.ys[0];
				double toOriginZ = originZ - quad.zs[0];

				double u = inverse * (toOriginX * crossX + toOriginY * crossY + toOriginZ * crossZ);

				if (u < 0.0D || u > 1.0D)
				{
					continue;
				}

				double qX = toOriginY * edge1Z - toOriginZ * edge1Y;
				double qY = toOriginZ * edge1X - toOriginX * edge1Z;
				double qZ = toOriginX * edge1Y - toOriginY * edge1X;

				double v = inverse * (dirX * qX + dirY * qY + dirZ * qZ);

				if (v < 0.0D || u + v > 1.0D)
				{
					continue;
				}

				double distance = inverse * (edge2X * qX + edge2Y * qY + edge2Z * qZ);

				if (distance <= EPSILON || distance >= bestDistance)
				{
					continue;
				}

				double texU = quad.us[0] + u * (quad.us[b] - quad.us[0]) + v * (quad.us[c] - quad.us[0]);
				double texV = quad.vs[0] + u * (quad.vs[b] - quad.vs[0]) + v * (quad.vs[c] - quad.vs[0]);

				int color = quad.texture != null
						? quad.texture.sample(wrap(texU), wrap(texV), lod)
						: shape.faces.sample(Math.max(quad.face, 0), wrap(texU), wrap(texV), lod);

				if ((color >>> 24) < 16)
				{
					continue;
				}

				color = BlockTextures.tint(color, quad.tint);

				bestDistance = distance;
				bestColor = color;
				bestFace = quad.face;
			}
		}

		if (bestColor == 0)
		{
			return false;
		}

		hit.color = bestColor;
		hit.face = bestFace;
		return true;
	}

	private static double wrap(double value)
	{
		double fraction = value - Math.floor(value);
		return fraction < 0.0D ? fraction + 1.0D : fraction;
	}
}
