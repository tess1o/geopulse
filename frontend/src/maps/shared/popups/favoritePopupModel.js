const getFavoriteName = (favorite, pending = false) => (
  favorite?.name || (pending ? 'Pending favorite' : 'Favorite')
)

const getFavoriteKindLabel = ({ pending = false, isArea = false } = {}) => {
  if (pending && isArea) return 'Pending area'
  if (pending) return 'Pending point'
  if (isArea) return 'Area favorite'
  return 'Favorite point'
}

export const buildFavoriteManagementPopupModel = (
  favorite,
  {
    pending = false,
    isArea = false
  } = {}
) => {
  const rows = [
    favorite?.category
      ? {
          label: 'Category',
          value: favorite.category
        }
      : null,
    favorite?.description
      ? {
          label: 'Description',
          value: favorite.description
        }
      : null,
    favorite?.address
      ? {
          label: 'Address',
          value: favorite.address
        }
      : null
  ].filter(Boolean)

  return {
    title: getFavoriteName(favorite, pending),
    subtitle: getFavoriteKindLabel({ pending, isArea }),
    iconClass: isArea ? 'pi pi-th-large' : 'pi pi-map-marker',
    rows,
    variant: 'compact'
  }
}
