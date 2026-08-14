export const getStayPlaceDetailsRoute = (stay) => {
  if (!stay || stay.type !== 'stay') {
    return null
  }

  if (stay.favoriteId) {
    return {
      name: 'Place Details',
      params: {
        type: 'favorite',
        id: stay.favoriteId
      }
    }
  }

  if (stay.geocodingId) {
    return {
      name: 'Place Details',
      params: {
        type: 'geocoding',
        id: stay.geocodingId
      }
    }
  }

  return null
}
